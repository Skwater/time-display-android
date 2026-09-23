package com.example.timedisplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SettingsPanel {
    private static final int PICK_IMAGE = 1;
    private static final int PICK_FONT = 2;
    private static final int PICK_VIDEO = 3;
    private static final String[] ZONES = {
            "SYSTEM", "UTC", "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Taipei",
            "Asia/Tokyo", "Asia/Seoul", "Asia/Singapore", "Asia/Manila",
            "Asia/Kuala_Lumpur", "Asia/Bangkok", "Asia/Jakarta", "Asia/Ho_Chi_Minh",
            "Asia/Kolkata", "Asia/Kathmandu", "Asia/Dhaka", "Asia/Dubai",
            "Asia/Riyadh", "Asia/Tehran", "Asia/Jerusalem", "Asia/Karachi",
            "Europe/Moscow", "Europe/London", "Europe/Dublin", "Europe/Paris",
            "Europe/Berlin", "Europe/Madrid", "Europe/Rome", "Europe/Amsterdam",
            "Europe/Zurich", "Europe/Istanbul", "Europe/Athens", "Europe/Warsaw",
            "Africa/Cairo", "Africa/Nairobi", "Africa/Johannesburg", "Africa/Lagos",
            "America/New_York", "America/Toronto", "America/Chicago",
            "America/Denver", "America/Los_Angeles", "America/Vancouver",
            "America/Mexico_City", "America/Sao_Paulo", "America/Argentina/Buenos_Aires",
            "America/Bogota", "America/Lima", "America/Anchorage",
            "Pacific/Honolulu", "Australia/Perth", "Australia/Adelaide",
            "Australia/Sydney", "Australia/Brisbane", "Pacific/Auckland", "Pacific/Fiji"
    };
    private static final String[] ORIENTATION_VALUES = {"auto", "portrait", "landscape"};
    private static final String[] MODE_VALUES = {"fill", "stretch"};
    private static final String[] FONT_VALUES = {"system", "sans", "serif", "mono", "custom"};

    private final MainActivity host;
    private final SharedPreferences prefs;
    private final List<Button> buttons = new ArrayList<>();
    private final List<SeekBar> sliders = new ArrayList<>();
    private final List<Switch> switches = new ArrayList<>();
    private LinearLayout content;
    private TextView backgroundInfo;
    private TextView brightnessInfo;
    private TextView fontInfo;

    SettingsPanel(MainActivity host) {
        this.host = host;
        this.prefs = ClockSettings.of(host);
    }

    void refreshAccent() {
        int accent = UiPalette.accent(host);
        for (Button button : buttons) {
            button.setBackgroundTintList(ColorStateList.valueOf(accent));
        }
        for (SeekBar slider : sliders) {
            ColorStateList color = ColorStateList.valueOf(accent);
            slider.setProgressTintList(color);
            slider.setThumbTintList(color);
        }
        for (Switch control : switches) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            control.setThumbTintList(new ColorStateList(states,
                    new int[]{accent, 0xFFB0BEC5}));
            control.setTrackTintList(new ColorStateList(states,
                    new int[]{UiPalette.withAlpha(accent, 150), 0x66708090}));
        }
    }

    ScrollView createSettingsPanel() {
        ScrollView panel = createPanel("设置");
        hint("向左滑动或点击面板外侧关闭");
        section("时间与日期");
        spinner("显示时区", zoneLabels(), ZONES, ClockSettings.ZONE, "SYSTEM");
        toggle("显示时区文字", ClockSettings.SHOW_ZONE, true);
        toggle("显示公历日期和星期", ClockSettings.SHOW_DATE, true);
        toggle("显示中国农历", ClockSettings.SHOW_LUNAR, false);
        toggle("显示秒数", ClockSettings.SHOW_SECONDS, true);
        section("屏幕");
        spinner("屏幕方向", new String[]{"跟随设备", "竖屏", "横屏"},
                ORIENTATION_VALUES, ClockSettings.ORIENTATION, "auto");
        section("界面");
        TextView transparencyInfo = label("", 16);
        content.addView(transparencyInfo);
        SeekBar transparency = new SeekBar(host);
        transparency.setMax(80);
        transparency.setProgress(Math.max(0, Math.min(80,
                prefs.getInt(ClockSettings.PANEL_TRANSPARENCY, 5))));
        transparencyInfo.setText("侧栏背景透明度：" + transparency.getProgress() + "%");
        transparency.setContentDescription("侧栏背景透明度，0% 到 80%");
        tintSlider(transparency);
        protectSlider(transparency);
        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                transparencyInfo.setText("侧栏背景透明度：" + progress + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.PANEL_TRANSPARENCY, progress).apply();
                    host.onSettingChanged(ClockSettings.PANEL_TRANSPARENCY);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(transparency, new LinearLayout.LayoutParams(-1, dp(48)));
        hint("仅调整左右侧栏背景；文字和时钟画面不变。");
        return panel;
    }

    ScrollView createCustomizationPanel() {
        ScrollView panel = createPanel("自定义");
        hint("向右滑动或点击面板外侧关闭");
        section("背景");
        spinner("背景适配", new String[]{"填充（保持比例并裁切）", "拉伸（铺满，可能变形）"},
                MODE_VALUES, ClockSettings.BACKGROUND_MODE, "fill");
        brightnessInfo = label("", 16);
        content.addView(brightnessInfo);
        SeekBar brightness = new SeekBar(host);
        brightness.setMax(70);
        brightness.setProgress(Math.max(0, Math.min(70, prefs.getInt(ClockSettings.BACKGROUND_DIM, 0))));
        brightnessInfo.setText("背景亮度：" + (100 - brightness.getProgress()) + "%");
        brightness.setContentDescription("背景亮度，100% 为原图亮度，最低 30%");
        tintSlider(brightness);
        protectSlider(brightness);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightnessInfo.setText("背景亮度：" + (100 - progress) + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.BACKGROUND_DIM, progress).apply();
                    host.onSettingChanged(ClockSettings.BACKGROUND_DIM);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(brightness, new LinearLayout.LayoutParams(-1, dp(48)));
        hint("100% 为原图亮度；向右拖动可降低背景亮度。");
        backgroundInfo = label("", 14);
        content.addView(backgroundInfo);
        LinearLayout mediaButtons = new LinearLayout(host);
        mediaButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button imageButton = createButton("选择图片", v -> pickImage());
        Button videoButton = createButton("选择视频", v -> pickVideo());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        imageParams.setMargins(0, dp(6), dp(3), 0);
        videoParams.setMargins(dp(3), dp(6), 0, 0);
        mediaButtons.addView(imageButton, imageParams);
        mediaButtons.addView(videoButton, videoParams);
        content.addView(mediaButtons);
        button("预览并调整背景位置", v -> host.startBackgroundPreview());
        button("恢复默认背景", v -> {
            prefs.edit().remove(ClockSettings.BACKGROUND_URI).remove(ClockSettings.BACKGROUND_TYPE)
                    .remove(ClockSettings.BACKGROUND_SCALE).remove(ClockSettings.BACKGROUND_PAN_X)
                    .remove(ClockSettings.BACKGROUND_PAN_Y).apply();
            updateInfo();
            host.onSettingChanged(ClockSettings.BACKGROUND_URI);
        });
        section("字体");
        spinner("字体样式", new String[]{"默认字体", "轻体无衬线", "衬线", "等宽", "自定义字体"},
                FONT_VALUES, ClockSettings.FONT, "system");
        toggle("文字加粗", ClockSettings.FONT_BOLD, false);
        toggle("文字阴影", ClockSettings.TEXT_SHADOW, true);
        fontInfo = label("", 14);
        content.addView(fontInfo);
        button("导入 TTF / OTF 字体", v -> pickFont());
        button("恢复默认字体", v -> {
            prefs.edit().putString(ClockSettings.FONT, "system").remove(ClockSettings.FONT_FILE).apply();
            updateInfo();
            host.onSettingChanged(ClockSettings.FONT);
        });
        hint("视频背景静音循环播放；长时间亮屏可能增加耗电。");
        updateInfo();
        return panel;
    }

    private ScrollView createPanel(String title) {
        ScrollView panel = new ScrollView(host);
        panel.setFillViewport(true);
        int transparency = Math.max(0, Math.min(80,
                prefs.getInt(ClockSettings.PANEL_TRANSPARENCY, 5)));
        panel.setBackgroundColor(Color.argb(Math.round(255 * (100 - transparency) / 100f),
                20, 29, 44));
        panel.setElevation(dp(12));
        panel.setContentDescription(title + "面板");
        content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        panel.addView(content);
        content.addView(label(title, 26));
        return panel;
    }

    private String[] zoneLabels() {
        String[] labels = new String[ZONES.length];
        Instant now = Instant.now();
        for (int i = 0; i < ZONES.length; i++) {
            String id = ZONES[i];
            String name = "SYSTEM".equals(id) ? "跟随系统"
                    : "UTC".equals(id) ? "UTC"
                    : id.substring(id.lastIndexOf('/') + 1).replace('_', ' ') + " (" + id + ")";
            try {
                ZoneId zone = "SYSTEM".equals(id) ? ZoneId.systemDefault() : ZoneId.of(id);
                ZoneOffset offset = zone.getRules().getOffset(now);
                int minutes = offset.getTotalSeconds() / 60;
                int absolute = Math.abs(minutes);
                String prefix = (minutes < 0 ? "-" : "+") + (absolute / 60)
                        + (absolute % 60 == 0 ? "" : String.format(Locale.ROOT, ":%02d", absolute % 60));
                labels[i] = prefix + "  " + name;
            } catch (RuntimeException exception) {
                labels[i] = name;
            }
        }
        return labels;
    }

    private void tintSlider(SeekBar slider) {
        sliders.add(slider);
        ColorStateList color = ColorStateList.valueOf(UiPalette.accent(host));
        slider.setProgressTintList(color);
        slider.setThumbTintList(color);
    }

    private void protectSlider(SeekBar slider) {
        slider.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                host.setDrawerGestureLocked(true);
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                host.setDrawerGestureLocked(false);
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private void spinner(String title, String[] labels, String[] values, String key, String fallback) {
        content.addView(label(title, 16));
        Spinner spinner = new Spinner(host);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(host, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int selected = 0;
        String saved = prefs.getString(key, fallback);
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selected = i;
        spinner.setSelection(selected);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        content.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = values[position];
                if (!value.equals(prefs.getString(key, fallback))) {
                    prefs.edit().putString(key, value).apply();
                    host.onSettingChanged(key);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void toggle(String title, String key, boolean fallback) {
        Switch control = new Switch(host);
        control.setText(title);
        control.setTextColor(Color.WHITE);
        control.setTextSize(16);
        control.setChecked(prefs.getBoolean(key, fallback));
        switches.add(control);
        int[][] states = {{android.R.attr.state_checked}, {}};
        int accent = UiPalette.accent(host);
        control.setThumbTintList(new ColorStateList(states,
                new int[]{accent, 0xFFB0BEC5}));
        control.setTrackTintList(new ColorStateList(states,
                new int[]{UiPalette.withAlpha(accent, 150), 0x66708090}));
        control.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            host.onSettingChanged(key);
        });
        content.addView(control, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private void section(String title) {
        TextView view = label(title, 20);
        view.setPadding(0, dp(22), 0, dp(8));
        content.addView(view);
    }

    private void hint(String text) {
        TextView view = label(text, 14);
        view.setTextColor(0xFFB0BECF);
        view.setPadding(0, dp(6), 0, dp(6));
        content.addView(view);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    private void button(String text, View.OnClickListener click) {
        Button button = createButton(text, click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.topMargin = dp(6);
        content.addView(button, params);
    }

    private Button createButton(String text, View.OnClickListener click) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(UiPalette.accent(host)));
        buttons.add(button);
        button.setOnClickListener(click);
        return button;
    }

    private void pickImage() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivityForResult(intent, PICK_IMAGE);
        } catch (ActivityNotFoundException unavailable) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            host.startActivityForResult(fallback, PICK_IMAGE);
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        host.startActivityForResult(intent, PICK_VIDEO);
    }

    private void pickFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        host.startActivityForResult(intent, PICK_FONT);
    }

    void onActivityResult(int request, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK_IMAGE) {
            try {
                String mime = host.getContentResolver().getType(uri);
                if (mime != null && !mime.startsWith("image/")) {
                    toast("请选择图片文件");
                    return;
                }
                File temporary = new File(host.getFilesDir(), "background-image-importing");
                File target = new File(host.getFilesDir(), "background-image");
                try (InputStream input = host.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalArgumentException("图片无法读取");
                    Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                prefs.edit().putString(ClockSettings.BACKGROUND_URI, Uri.fromFile(target).toString())
                        .putString(ClockSettings.BACKGROUND_TYPE, "image")
                        .remove(ClockSettings.BACKGROUND_SCALE).remove(ClockSettings.BACKGROUND_PAN_X)
                        .remove(ClockSettings.BACKGROUND_PAN_Y)
                        .apply();
                updateInfo();
                host.onSettingChanged(ClockSettings.BACKGROUND_URI);
            } catch (Exception error) {
                toast("图片导入失败，请重试");
            }
        } else if (request == PICK_VIDEO) {
            try {
                String mime = host.getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("video/")) {
                    toast("请选择视频文件");
                    return;
                }
                host.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString(ClockSettings.BACKGROUND_URI, uri.toString())
                        .putString(ClockSettings.BACKGROUND_TYPE, "video")
                        .remove(ClockSettings.BACKGROUND_SCALE).remove(ClockSettings.BACKGROUND_PAN_X)
                        .remove(ClockSettings.BACKGROUND_PAN_Y).apply();
                updateInfo();
                host.onSettingChanged(ClockSettings.BACKGROUND_URI);
            } catch (Exception error) {
                toast("无法保存视频文件的访问权限");
            }
        } else if (request == PICK_FONT) {
            String name = displayName(uri).toLowerCase(Locale.ROOT);
            if (!name.endsWith(".ttf") && !name.endsWith(".otf")) {
                toast("请选择 TTF 或 OTF 字体");
                return;
            }
            File target = new File(host.getFilesDir(), "custom-font" + (name.endsWith(".otf") ? ".otf" : ".ttf"));
            File temporary = new File(host.getCacheDir(), "importing-font");
            try (InputStream input = host.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalArgumentException("文件不可读");
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 20L * 1024 * 1024) throw new IllegalArgumentException("字体超过 20 MB");
                    output.write(buffer, 0, count);
                }
                Typeface.createFromFile(temporary);
                if (!temporary.renameTo(target)) {
                    try (InputStream source = new java.io.FileInputStream(temporary);
                         FileOutputStream destination = new FileOutputStream(target)) {
                        while ((count = source.read(buffer)) != -1) destination.write(buffer, 0, count);
                    }
                }
                prefs.edit().putString(ClockSettings.FONT_FILE, target.getAbsolutePath())
                        .putString(ClockSettings.FONT, "custom").apply();
                updateInfo();
                host.onSettingChanged(ClockSettings.FONT);
                toast("字体已导入");
            } catch (Exception error) {
                toast("字体导入失败：请检查格式与大小");
            } finally {
                temporary.delete();
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = host.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private void updateInfo() {
        String uri = prefs.getString(ClockSettings.BACKGROUND_URI, "");
        backgroundInfo.setText(uri.isEmpty() ? "当前：深色纯色背景"
                : "当前：" + ("video".equals(prefs.getString(ClockSettings.BACKGROUND_TYPE, "image")) ? "视频" : "图片") + "文件");
        fontInfo.setText(prefs.getString(ClockSettings.FONT_FILE, "").isEmpty()
                ? "当前：系统字体" : "已导入本地字体");
    }

    private void toast(String text) { Toast.makeText(host, text, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }
}
