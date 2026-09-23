package com.example.timedisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class FontLibrary {
    private static final String PREFIX = "font:";
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    static final class Entry {
        final String id;
        final String name;

        Entry(String id, String name) {
            this.id = id;
            this.name = name;
        }

        String value() { return "legacy".equals(id) ? "custom" : PREFIX + id; }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final File directory;
    private final List<Entry> entries = new ArrayList<>();

    FontLibrary(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
        directory = new File(context.getFilesDir(), "fonts");
        readEntries();
        migrateLegacyFont();
    }

    List<Entry> entries() {
        List<Entry> available = new ArrayList<>(entries);
        File legacy = legacyFile();
        if (legacy != null && legacy.isFile()) {
            available.add(new Entry("legacy", readFamilyName(legacy, "旧版字体")));
        }
        return available;
    }

    Entry find(String value) {
        for (Entry entry : entries()) if (entry.value().equals(value)) return entry;
        return null;
    }

    Entry importFont(Uri uri, String filename) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return importStream(input, filename, true);
        }
    }

    boolean remove(Entry entry) {
        if (entry != null && "legacy".equals(entry.id)) {
            File legacy = legacyFile();
            if (legacy == null || (legacy.exists() && !legacy.delete())) return false;
            SharedPreferences.Editor edit = prefs.edit().remove(ClockSettings.FONT_FILE);
            if ("custom".equals(prefs.getString(ClockSettings.FONT, "system"))) {
                edit.putString(ClockSettings.FONT, "system");
            }
            return edit.commit();
        }
        if (entry == null || !entries.contains(entry)) return false;
        File file = fileFor(context, entry.value());
        if (file == null || (file.exists() && !file.delete())) return false;
        entries.remove(entry);
        SharedPreferences.Editor edit = prefs.edit().putString(ClockSettings.FONT_LIBRARY, serialize());
        if (entry.value().equals(prefs.getString(ClockSettings.FONT, "system"))) {
            edit.putString(ClockSettings.FONT, "system").remove(ClockSettings.FONT_FILE);
        }
        return edit.commit();
    }

    static File fileFor(Context context, String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        String id = value.substring(PREFIX.length());
        if (!id.matches("[0-9a-f-]{36}\\.(ttf|otf)")) return null;
        return new File(new File(context.getFilesDir(), "fonts"), id);
    }

    private Entry importStream(InputStream input, String filename, boolean select) throws Exception {
        if (input == null) throw new IllegalArgumentException("字体文件无法读取");
        String lower = filename.toLowerCase(Locale.ROOT);
        String extension = lower.endsWith(".otf") ? ".otf" : lower.endsWith(".ttf") ? ".ttf" : null;
        if (extension == null) throw new IllegalArgumentException("仅支持 TTF / OTF");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建字体目录");
        File temporary = File.createTempFile("incoming-", extension, directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_SIZE) throw new IllegalArgumentException("字体超过 20 MB");
                    output.write(buffer, 0, count);
                }
            }
            if (Typeface.createFromFile(temporary) == null) throw new IllegalArgumentException("字体格式无效");
            String fallback = filename.substring(0, filename.length() - extension.length());
            String name = uniqueName(readFamilyName(temporary, fallback));
            String id = UUID.randomUUID().toString() + extension;
            File target = new File(directory, id);
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Entry entry = new Entry(id, name);
            entries.add(entry);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(ClockSettings.FONT_LIBRARY, serialize()).remove(ClockSettings.FONT_FILE);
            if (select) edit.putString(ClockSettings.FONT, entry.value());
            if (!edit.commit()) {
                entries.remove(entry);
                target.delete();
                throw new IllegalStateException("无法保存字体设置");
            }
            return entry;
        } finally {
            temporary.delete();
        }
    }

    private String uniqueName(String name) {
        String base = name.isEmpty() ? "导入字体" : name;
        String candidate = base;
        int suffix = 2;
        boolean used;
        do {
            used = false;
            for (Entry entry : entries) if (entry.name.equals(candidate)) used = true;
            if (used) candidate = base + " " + suffix++;
        } while (used);
        return candidate;
    }

    private void readEntries() {
        try {
            JSONArray json = new JSONArray(prefs.getString(ClockSettings.FONT_LIBRARY, "[]"));
            for (int i = 0; i < json.length(); i++) {
                JSONObject item = json.getJSONObject(i);
                String id = item.getString("id");
                String name = item.getString("name");
                File file = fileFor(context, PREFIX + id);
                if (file != null && file.isFile() && !name.trim().isEmpty()) {
                    entries.add(new Entry(id, name));
                }
            }
        } catch (JSONException | ClassCastException ignored) {
            entries.clear();
        }
    }

    private String serialize() {
        JSONArray json = new JSONArray();
        try {
            for (Entry entry : entries) {
                json.put(new JSONObject().put("id", entry.id).put("name", entry.name));
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return json.toString();
    }

    private void migrateLegacyFont() {
        File legacy = legacyFile();
        if (legacy == null || !legacy.isFile()) return;
        try (InputStream input = new FileInputStream(legacy)) {
            importStream(input, legacy.getName(),
                    "custom".equals(prefs.getString(ClockSettings.FONT, "system")));
        } catch (Exception ignored) {
            // The old file and preference remain usable if migration cannot finish.
        }
    }

    private File legacyFile() {
        String path = prefs.getString(ClockSettings.FONT_FILE, "");
        if (path.isEmpty()) return null;
        try {
            File file = new File(path).getCanonicalFile();
            String root = context.getFilesDir().getCanonicalPath() + File.separator;
            return file.getPath().startsWith(root) ? file : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String readFamilyName(File file, String fallback) {
        String best = null;
        int bestScore = -1;
        try (RandomAccessFile data = new RandomAccessFile(file, "r")) {
            long size = data.length();
            if (size < 12) return cleanName(fallback);
            data.seek(4);
            int tables = Math.min(data.readUnsignedShort(), 256);
            long nameOffset = -1;
            long nameLength = 0;
            for (int i = 0; i < tables && 12L + 16L * (i + 1) <= size; i++) {
                data.seek(12L + 16L * i);
                int tag = data.readInt();
                data.readInt();
                long offset = Integer.toUnsignedLong(data.readInt());
                long length = Integer.toUnsignedLong(data.readInt());
                if (tag == 0x6e616d65 && offset <= size && length <= size - offset) {
                    nameOffset = offset;
                    nameLength = length;
                    break;
                }
            }
            if (nameOffset < 0 || nameLength < 6) return cleanName(fallback);
            data.seek(nameOffset + 2);
            int count = Math.min(data.readUnsignedShort(), 1024);
            int stringsOffset = data.readUnsignedShort();
            for (int i = 0; i < count && 6L + 12L * (i + 1) <= nameLength; i++) {
                data.seek(nameOffset + 6L + 12L * i);
                int platform = data.readUnsignedShort();
                int encoding = data.readUnsignedShort();
                int language = data.readUnsignedShort();
                int nameId = data.readUnsignedShort();
                int length = data.readUnsignedShort();
                int offset = data.readUnsignedShort();
                if ((nameId != 1 && nameId != 16) || length == 0 || length > 512) continue;
                long start = (long) stringsOffset + offset;
                if (start < 0 || start + length > nameLength) continue;
                int localeScore = "zh".equals(Locale.getDefault().getLanguage())
                        && (language == 0x0804 || language == 0x0404) ? 8
                        : language == 0x0409 ? 4 : 0;
                int score = (nameId == 16 ? 40 : 20)
                        + (platform == 3 ? 10 : platform == 0 ? 8 : 0) + localeScore;
                if (score <= bestScore) continue;
                data.seek(nameOffset + start);
                byte[] bytes = new byte[length];
                data.readFully(bytes);
                String value = platform == 0 || platform == 3
                        ? new String(bytes, StandardCharsets.UTF_16BE)
                        : platform == 1 && encoding == 0
                        ? new String(bytes, StandardCharsets.ISO_8859_1) : "";
                value = cleanName(value);
                if (!value.isEmpty()) {
                    best = value;
                    bestScore = score;
                }
            }
        } catch (Exception ignored) { }
        return best == null ? cleanName(fallback) : best;
    }

    private static String cleanName(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() > 60 ? clean.substring(0, 60).trim() : clean;
    }
}
