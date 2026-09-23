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
import java.util.Locale;
import java.util.Set;

final class PlaylistFolderImporter {
    static final class Result {
        int recognized;
        int added;
        int failed;
        int skipped;
    }

    private static final class Folder {
        final String id;
        Folder(String id) { this.id = id; }
    }

    private static final class Child {
        final String id, name, mime;
        Child(String id, String name, String mime) {
            this.id = id;
            this.name = name;
            this.mime = mime;
        }
    }

    private interface MediaVisitor {
        void visit(Uri uri, String name, String type);
    }

    private PlaylistFolderImporter() { }

    static Result scanTree(Context context, Uri treeUri) {
        Result result = new Result();
        visitTree(context, treeUri, result, (uri, name, type) -> result.recognized++);
        return result;
    }

    static Result importTree(Context context, Uri treeUri, PlaylistStore store, long playlistId) {
        Result result = new Result();
        visitTree(context, treeUri, result, (uri, name, type) -> {
            result.recognized++;
            try {
                if ("image".equals(type)) store.addLinkedImageTo(playlistId, uri, name, treeUri);
                else store.addVideoTo(playlistId, uri, name, treeUri);
                result.added++;
            } catch (Exception error) {
                result.failed++;
            }
        });
        return result;
    }

    private static void visitTree(Context context, Uri treeUri, Result result, MediaVisitor visitor) {
        ContentResolver resolver = context.getContentResolver();
        ArrayDeque<Folder> folders = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        folders.add(new Folder(DocumentsContract.getTreeDocumentId(treeUri)));
        while (!folders.isEmpty()) {
            Folder folder = folders.removeFirst();
            if (!visited.add(folder.id)) continue;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.id);
            String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE};
            List<Child> entries = new ArrayList<>();
            try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
                if (cursor == null) {
                    result.failed++;
                    continue;
                }
                int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
                while (cursor.moveToNext()) entries.add(new Child(cursor.getString(idColumn),
                        cursor.getString(nameColumn), cursor.getString(mimeColumn)));
            } catch (Exception error) {
                result.failed++;
                continue;
            }
            entries.sort(Comparator.comparing(child ->
                    child.name == null ? "" : child.name.toLowerCase(Locale.ROOT)));
            for (Child child : entries) {
                if (child.id == null || child.id.isEmpty()) {
                    result.skipped++;
                    continue;
                }
                String name = child.name == null || child.name.isEmpty() ? child.id : child.name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(child.mime)) {
                    folders.addLast(new Folder(child.id));
                    continue;
                }
                String type = recognizedType(child.mime, name);
                if (type == null) {
                    result.skipped++;
                    continue;
                }
                Uri mediaUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id);
                visitor.visit(mediaUri, name, type);
            }
        }
    }

    private static String recognizedType(String mime, String name) {
        if (mime != null) {
            if (mime.startsWith("image/")) return "image";
            if (mime.startsWith("video/")) return "video";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif)$")) return "image";
        if (lower.matches(".*\\.(mp4|m4v|mkv|webm|mov|avi|3gp|3g2)$")) return "video";
        return null;
    }
}
