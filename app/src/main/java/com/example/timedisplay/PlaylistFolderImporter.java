package com.example.timedisplay;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PlaylistFolderImporter {
    private static final int MAX_SCANNED_DOCUMENTS = 10000;

    static final class Result {
        int added;
        int failed;
        int skipped;
        boolean limitReached;
        boolean scanIncomplete;
    }

    private static final class Folder {
        final String id;
        final String path;

        Folder(String id, String path) {
            this.id = id;
            this.path = path;
        }
    }

    private static final class Media {
        final Uri uri;
        final String name;
        final String type;
        final String sortKey;

        Media(Uri uri, String name, String type, String sortKey) {
            this.uri = uri;
            this.name = name;
            this.type = type;
            this.sortKey = sortKey;
        }
    }

    private PlaylistFolderImporter() { }

    static Result importTree(Context context, Uri treeUri, PlaylistStore store) {
        Result result = new Result();
        ContentResolver resolver = context.getContentResolver();
        ArrayDeque<Folder> folders = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<Media> found = new ArrayList<>();
        folders.add(new Folder(DocumentsContract.getTreeDocumentId(treeUri), ""));
        int scanned = 0;
        while (!folders.isEmpty() && scanned < MAX_SCANNED_DOCUMENTS) {
            Folder folder = folders.removeFirst();
            if (!visited.add(folder.id)) continue;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.id);
            String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE};
            try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
                if (cursor == null) {
                    result.failed++;
                    continue;
                }
                int idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
                while (cursor.moveToNext() && scanned < MAX_SCANNED_DOCUMENTS) {
                    scanned++;
                    String id = cursor.getString(idIndex);
                    String name = cursor.getString(nameIndex);
                    String mime = cursor.getString(mimeIndex);
                    if (id == null || id.isEmpty()) {
                        result.skipped++;
                        continue;
                    }
                    if (name == null || name.isEmpty()) name = id;
                    String path = folder.path + name;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        folders.addLast(new Folder(id, path + "/"));
                    } else {
                        String type = recognizedType(mime, name);
                        if (type == null) {
                            result.skipped++;
                            continue;
                        }
                        Uri mediaUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                        found.add(new Media(mediaUri, name, type, path));
                    }
                }
            } catch (Exception error) {
                result.failed++;
            }
        }
        if (!folders.isEmpty() || scanned >= MAX_SCANNED_DOCUMENTS) result.scanIncomplete = true;
        found.sort(Comparator.comparing(item -> item.sortKey.toLowerCase(java.util.Locale.ROOT)));
        int remaining = Math.max(0, PlaylistStore.MAX_ITEMS - store.entries().size());
        for (int i = 0; i < found.size(); i++) {
            if (remaining <= 0) {
                result.limitReached = true;
                result.skipped += found.size() - i;
                break;
            }
            Media media = found.get(i);
            try {
                if ("image".equals(media.type)) store.addImage(media.uri, media.name);
                else store.addVideo(media.uri, media.name, treeUri);
                result.added++;
                remaining--;
            } catch (Exception error) {
                result.failed++;
            }
        }
        return result;
    }

    private static String recognizedType(String mime, String name) {
        if (mime != null) {
            if (mime.startsWith("image/")) return "image";
            if (mime.startsWith("video/")) return "video";
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif)$")) return "image";
        if (lower.matches(".*\\.(mp4|m4v|mkv|webm|mov|avi|3gp|3g2)$")) return "video";
        return null;
    }
}
