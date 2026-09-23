package com.example.timedisplay;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PlaylistStore {
    static final class Entry {
        final String id, uri, type, name, grantUri;
        Entry(String id, String uri, String type, String name, String grantUri) {
            this.id = id;
            this.uri = uri;
            this.type = type;
            this.name = name;
            this.grantUri = grantUri;
        }
    }

    static final class SavedList {
        final long id;
        final String name;
        final int size;
        SavedList(long id, String name, int size) {
            this.id = id;
            this.name = name;
            this.size = size;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Helper helper;

    PlaylistStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = ClockSettings.of(this.context);
        helper = new Helper(this.context);
    }

    long activeId() {
        SQLiteDatabase db = helper.getReadableDatabase();
        long saved = prefs.getLong(ClockSettings.PLAYLIST_ACTIVE_ID, 1L);
        if (listExists(db, saved)) return saved;
        try (Cursor cursor = db.rawQuery("SELECT id FROM playlists ORDER BY id LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                long fallback = cursor.getLong(0);
                prefs.edit().putLong(ClockSettings.PLAYLIST_ACTIVE_ID, fallback).apply();
                return fallback;
            }
        }
        return 1L;
    }

    boolean select(long id) {
        if (!listExists(helper.getReadableDatabase(), id)) return false;
        return prefs.edit().putLong(ClockSettings.PLAYLIST_ACTIVE_ID, id).commit();
    }

    List<SavedList> savedLists() {
        List<SavedList> result = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT p.id,p.name,COUNT(i.id) FROM playlists p LEFT JOIN items i ON i.playlist_id=p.id "
                        + "GROUP BY p.id ORDER BY p.id", null)) {
            while (cursor.moveToNext()) result.add(new SavedList(cursor.getLong(0),
                    cursor.getString(1), cursor.getInt(2)));
        }
        return result;
    }

    long createEmpty(String name) {
        try {
            return helper.getWritableDatabase().insert("playlists", null, listValues(name));
        } catch (RuntimeException error) {
            return -1;
        }
    }

    long saveCopy(String name) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long source = activeId();
        db.beginTransaction();
        try {
            long created = db.insert("playlists", null, listValues(name));
            if (created < 0) return -1;
            db.execSQL("INSERT INTO items(id,playlist_id,position,uri,type,name,grant_uri) "
                            + "SELECT lower(hex(randomblob(16))),?,position,uri,type,name,grant_uri "
                            + "FROM items WHERE playlist_id=? ORDER BY position",
                    new Object[]{created, source});
            db.setTransactionSuccessful();
            return created;
        } catch (RuntimeException error) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    boolean rename(long id, String name) {
        try {
            ContentValues values = listValues(name);
            return helper.getWritableDatabase().update("playlists", values, "id=?",
                    new String[]{Long.toString(id)}) == 1;
        } catch (RuntimeException error) {
            return false;
        }
    }

    boolean deleteList(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!listExists(db, id)) return false;
        List<Entry> removed = entriesFor(db, id, 0, -1);
        db.beginTransaction();
        try {
            db.delete("items", "playlist_id=?", new String[]{Long.toString(id)});
            db.delete("playlists", "id=?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        List<SavedList> remaining = savedLists();
        long next = remaining.isEmpty() ? createEmpty(L10n.text(context,
                "默认播放列表", "Default playlist")) : remaining.get(0).id;
        if (!listExists(db, prefs.getLong(ClockSettings.PLAYLIST_ACTIVE_ID, 1L))) select(next);
        for (Entry entry : removed) cleanupIfUnused(db, entry);
        return true;
    }

    int clearActive() {
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = activeId();
        List<Entry> removed = entriesFor(db, id, 0, -1);
        int count = db.delete("items", "playlist_id=?", new String[]{Long.toString(id)});
        for (Entry entry : removed) cleanupIfUnused(db, entry);
        return count;
    }

    int count() {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM items WHERE playlist_id=?",
                new String[]{Long.toString(activeId())})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    List<Entry> entries() { return entriesFor(helper.getReadableDatabase(), activeId(), 0, -1); }
    List<Entry> entries(int offset, int limit) {
        return entriesFor(helper.getReadableDatabase(), activeId(), offset, limit);
    }

    Entry addImage(Uri source, String name) throws Exception { return addImageTo(activeId(), source, name); }
    Entry addImageTo(long listId, Uri source, String name) throws Exception {
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
            if (!insert(listId, entry)) throw new IllegalStateException("could not save image");
            return entry;
        } catch (Exception error) {
            temporary.delete();
            target.delete();
            throw error;
        }
    }

    Entry addVideo(Uri source, String name) { return addVideoTo(activeId(), source, name, null); }
    Entry addVideoTo(long listId, Uri source, String name, Uri treeGrant) {
        Entry entry = new Entry(UUID.randomUUID().toString(), source.toString(), "video", name,
                treeGrant == null ? "" : treeGrant.toString());
        if (!insert(listId, entry)) throw new IllegalStateException("could not save video");
        return entry;
    }

    boolean remove(String id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long listId = activeId();
        Entry entry = find(db, listId, id);
        int old = positionOf(db, listId, id);
        if (entry == null || old < 0) return false;
        db.beginTransaction();
        try {
            db.delete("items", "id=? AND playlist_id=?", new String[]{id, Long.toString(listId)});
            db.execSQL("UPDATE items SET position=position-1 WHERE playlist_id=? AND position>?",
                    new Object[]{listId, old});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupIfUnused(db, entry);
        return true;
    }

    boolean move(String id, int delta) {
        int old = positionOf(helper.getReadableDatabase(), activeId(), id);
        return old >= 0 && moveTo(id, old + delta);
    }

    boolean moveTo(String id, int target) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long listId = activeId();
        int old = positionOf(db, listId, id);
        if (old < 0 || target < 0 || target >= count()) return false;
        if (old == target) return true;
        db.beginTransaction();
        try {
            if (old < target) db.execSQL("UPDATE items SET position=position-1 "
                            + "WHERE playlist_id=? AND position>? AND position<=?",
                    new Object[]{listId, old, target});
            else db.execSQL("UPDATE items SET position=position+1 "
                            + "WHERE playlist_id=? AND position>=? AND position<?",
                    new Object[]{listId, target, old});
            ContentValues values = new ContentValues();
            values.put("position", target);
            db.update("items", values, "id=? AND playlist_id=?",
                    new String[]{id, Long.toString(listId)});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    boolean usesTree(Uri treeUri) { return used(helper.getReadableDatabase(), "grant_uri", treeUri.toString()); }

    private boolean insert(long listId, Entry entry) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!listExists(db, listId)) return false;
        ContentValues values = new ContentValues();
        values.put("id", entry.id);
        values.put("playlist_id", listId);
        values.put("position", nextPosition(db, listId));
        values.put("uri", entry.uri);
        values.put("type", entry.type);
        values.put("name", entry.name);
        values.put("grant_uri", entry.grantUri);
        return db.insert("items", null, values) != -1;
    }

    private List<Entry> entriesFor(SQLiteDatabase db, long listId, int offset, int limit) {
        List<Entry> result = new ArrayList<>();
        String sql = "SELECT id,uri,type,name,grant_uri FROM items WHERE playlist_id=? "
                + "ORDER BY position,id" + (limit > 0 ? " LIMIT " + limit + " OFFSET " + offset : "");
        try (Cursor cursor = db.rawQuery(sql, new String[]{Long.toString(listId)})) {
            while (cursor.moveToNext()) result.add(new Entry(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4)));
        }
        return result;
    }

    private Entry find(SQLiteDatabase db, long listId, String id) {
        try (Cursor cursor = db.rawQuery(
                "SELECT id,uri,type,name,grant_uri FROM items WHERE id=? AND playlist_id=?",
                new String[]{id, Long.toString(listId)})) {
            return cursor.moveToFirst() ? new Entry(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4)) : null;
        }
    }

    private int positionOf(SQLiteDatabase db, long listId, String id) {
        try (Cursor cursor = db.rawQuery("SELECT position FROM items WHERE id=? AND playlist_id=?",
                new String[]{id, Long.toString(listId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private int nextPosition(SQLiteDatabase db, long listId) {
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM items WHERE playlist_id=?",
                new String[]{Long.toString(listId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private boolean listExists(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM playlists WHERE id=?",
                new String[]{Long.toString(id)})) {
            return cursor.moveToFirst();
        }
    }

    private boolean used(SQLiteDatabase db, String column, String value) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM items WHERE " + column + "=? LIMIT 1",
                new String[]{value})) {
            return cursor.moveToFirst();
        }
    }

    private void cleanupIfUnused(SQLiteDatabase db, Entry entry) {
        if ("image".equals(entry.type) && !used(db, "uri", entry.uri)) {
            try {
                File directory = new File(context.getFilesDir(), "playlist-images").getCanonicalFile();
                File file = new File(Uri.parse(entry.uri).getPath()).getCanonicalFile();
                if (directory.equals(file.getParentFile())) file.delete();
            } catch (Exception ignored) { }
        } else if ("video".equals(entry.type)) {
            String grant = entry.grantUri.isEmpty() ? entry.uri : entry.grantUri;
            if (!entry.grantUri.isEmpty() && used(db, "grant_uri", grant)) return;
            if (entry.grantUri.isEmpty() && (used(db, "uri", entry.uri)
                    || entry.uri.equals(prefs.getString(ClockSettings.BACKGROUND_URI, "")))) return;
            try {
                context.getContentResolver().releasePersistableUriPermission(Uri.parse(grant),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
    }

    private ContentValues listValues(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        return values;
    }

    private static final class Helper extends SQLiteOpenHelper {
        private final Context context;
        Helper(Context context) {
            super(context, "playlists.db", null, 1);
            this.context = context;
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
            db.execSQL("CREATE TABLE items(id TEXT PRIMARY KEY,playlist_id INTEGER NOT NULL,"
                    + "position INTEGER NOT NULL,uri TEXT NOT NULL,type TEXT NOT NULL,"
                    + "name TEXT NOT NULL,grant_uri TEXT NOT NULL DEFAULT '')");
            db.execSQL("CREATE INDEX items_order ON items(playlist_id,position)");
            ContentValues list = new ContentValues();
            list.put("name", L10n.text(context, "默认播放列表", "Default playlist"));
            long id = db.insertOrThrow("playlists", null, list);
            SharedPreferences prefs = ClockSettings.of(context);
            try {
                JSONArray old = new JSONArray(prefs.getString(ClockSettings.PLAYLIST_ITEMS, "[]"));
                for (int i = 0; i < old.length(); i++) {
                    JSONObject item = old.optJSONObject(i);
                    if (item == null) continue;
                    String uri = item.optString("uri", "");
                    String type = item.optString("type", "");
                    if (uri.isEmpty() || !("image".equals(type) || "video".equals(type))) continue;
                    ContentValues values = new ContentValues();
                    values.put("id", item.optString("id", UUID.randomUUID().toString()));
                    values.put("playlist_id", id);
                    values.put("position", i);
                    values.put("uri", uri);
                    values.put("type", type);
                    values.put("name", item.optString("name", type));
                    values.put("grant_uri", item.optString("grant", ""));
                    db.insert("items", null, values);
                }
            } catch (Exception ignored) { }
            prefs.edit().putLong(ClockSettings.PLAYLIST_ACTIVE_ID, id).commit();
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    }
}
