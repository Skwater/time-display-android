package com.example.timedisplay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class PlaylistStore {
    static final int MAX_ITEMS = 100;

    static final class Entry {
        final String id;
        final String uri;
        final String type;
        final String name;
        final String grantUri;

        Entry(String id, String uri, String type, String name, String grantUri) {
            this.id = id;
            this.uri = uri;
            this.type = type;
            this.name = name;
            this.grantUri = grantUri;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;

    PlaylistStore(Context context) {
        this.context = context;
        prefs = ClockSettings.of(context);
    }

    List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(ClockSettings.PLAYLIST_ITEMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id");
                String uri = item.optString("uri");
                String type = item.optString("type");
                if (id.isEmpty() || uri.isEmpty() || !("image".equals(type) || "video".equals(type))) continue;
                result.add(new Entry(id, uri, type, item.optString("name", type),
                        item.optString("grant", "")));
            }
        } catch (Exception ignored) { }
        return result;
    }

    Entry addImage(Uri source, String name) throws Exception {
        List<Entry> items = entries();
        if (items.size() >= MAX_ITEMS) throw new IllegalStateException("playlist full");
        File directory = new File(context.getFilesDir(), "playlist-images");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("directory unavailable");
        String id = UUID.randomUUID().toString();
        File target = new File(directory, id);
        File temporary = new File(directory, id + ".tmp");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalArgumentException("image unavailable");
                Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Entry entry = new Entry(id, Uri.fromFile(target).toString(), "image", name, "");
            items.add(entry);
            if (!save(items)) throw new IllegalStateException("could not save playlist");
            return entry;
        } catch (Exception error) {
            temporary.delete();
            target.delete();
            throw error;
        }
    }

    Entry addVideo(Uri source, String name) {
        return addVideo(source, name, null);
    }

    Entry addVideo(Uri source, String name, Uri treeGrant) {
        List<Entry> items = entries();
        if (items.size() >= MAX_ITEMS) throw new IllegalStateException("playlist full");
        Entry entry = new Entry(UUID.randomUUID().toString(), source.toString(), "video", name,
                treeGrant == null ? "" : treeGrant.toString());
        items.add(entry);
        if (!save(items)) throw new IllegalStateException("could not save playlist");
        return entry;
    }

    boolean remove(String id) {
        List<Entry> items = entries();
        for (int i = 0; i < items.size(); i++) {
            Entry entry = items.get(i);
            if (!entry.id.equals(id)) continue;
            items.remove(i);
            if (!save(items)) return false;
            if ("image".equals(entry.type)) {
                File file = new File(new File(context.getFilesDir(), "playlist-images"), entry.id);
                file.delete();
            } else if ("video".equals(entry.type) && !entry.grantUri.isEmpty()) {
                if (!usesTree(items, entry.grantUri)) releaseGrant(entry.grantUri);
            } else if ("video".equals(entry.type)
                    && !entry.uri.equals(prefs.getString(ClockSettings.BACKGROUND_URI, ""))) {
                boolean stillUsed = false;
                for (Entry remaining : items) {
                    if (entry.uri.equals(remaining.uri)) {
                        stillUsed = true;
                        break;
                    }
                }
                if (!stillUsed) {
                    releaseGrant(entry.uri);
                }
            }
            return true;
        }
        return false;
    }

    boolean usesTree(Uri treeUri) {
        return usesTree(entries(), treeUri.toString());
    }

    private boolean usesTree(List<Entry> items, String grantUri) {
        for (Entry item : items) if (grantUri.equals(item.grantUri)) return true;
        return false;
    }

    private void releaseGrant(String uri) {
        try {
            context.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    boolean move(String id, int delta) {
        List<Entry> items = entries();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).id.equals(id)) continue;
            int target = i + delta;
            if (target < 0 || target >= items.size()) return false;
            Collections.swap(items, i, target);
            return save(items);
        }
        return false;
    }

    boolean moveTo(String id, int target) {
        List<Entry> items = entries();
        if (target < 0 || target >= items.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).id.equals(id)) continue;
            if (i == target) return true;
            Entry moved = items.remove(i);
            items.add(target, moved);
            return save(items);
        }
        return false;
    }

    private boolean save(List<Entry> items) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : items) {
                JSONObject item = new JSONObject();
                item.put("id", entry.id);
                item.put("uri", entry.uri);
                item.put("type", entry.type);
                item.put("name", entry.name);
                item.put("grant", entry.grantUri);
                array.put(item);
            }
        } catch (Exception error) {
            return false;
        }
        return prefs.edit().putString(ClockSettings.PLAYLIST_ITEMS, array.toString()).commit();
    }
}
