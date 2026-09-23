package com.example.timedisplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
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
    private static final String[] MODE_VALUES = {"fill", "fit", "stretch", "tile"};
    private static final String[] LANGUAGE_VALUES = {"system", "zh", "en"};
    private static final String[] FONT_VALUES = {"system", "sans", "serif", "mono"};
    private static final String[] COLOR_KEYS = {ClockSettings.FONT_OPACITY,
            ClockSettings.FONT_INTENSITY, ClockSettings.FONT_HUE, ClockSettings.FONT_SATURATION};

    private final MainActivity host;
    private final SharedPreferences prefs;
    private final FontLibrary fonts;
    private final List<Button> buttons = new ArrayList<>();
    private final List<SeekBar> sliders = new ArrayList<>();
    private final List<Switch> switches = new ArrayList<>();
    private final List<SeekBar> colorSliders = new ArrayList<>();
    private LinearLayout content;
    private TextView backgroundInfo;
    private TextView brightnessInfo;
    private TextView fontInfo;
    private TextView fontMenuLabel;
    private TextView colorHeaderLabel;
    private View colorPreview;
    private LinearLayout colorControls;
    private PopupWindow fontPopup;

    SettingsPanel(MainActivity host) {
        this.host = host;
        this.prefs = ClockSettings.of(host);
        this.fonts = new FontLibrary(host, prefs);
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
        refreshColorThumbs();
        refreshColorTracks();
    }

    ScrollView createSettingsPanel() {
        ScrollView panel = createPanel(t("设置", "Settings"));
        hint(t("向左滑动或点击面板外侧关闭", "Swipe left or tap outside to close"));
        section(t("语言", "Language"));
        spinner(t("应用语言", "App language"),
                new String[]{t("跟随系统", "Follow system"), "中文", "English"},
                LANGUAGE_VALUES, ClockSettings.LANGUAGE, "system");
        section(t("时间与日期", "Time and date"));
        spinner(t("显示时区", "Time zone"), zoneLabels(), ZONES, ClockSettings.ZONE, "SYSTEM");
        toggle(t("显示时区文字", "Show time zone"), ClockSettings.SHOW_ZONE, true);
        toggle(t("显示公历日期和星期", "Show date and weekday"), ClockSettings.SHOW_DATE, true);
        toggle(t("显示中国农历", "Show Chinese lunar date"), ClockSettings.SHOW_LUNAR, false);
        toggle(t("显示秒数", "Show seconds"), ClockSettings.SHOW_SECONDS, true);
        section(t("屏幕", "Screen"));
        spinner(t("屏幕方向", "Orientation"),
                new String[]{t("跟随设备", "Follow device"), t("竖屏", "Portrait"), t("横屏", "Landscape")},
                ORIENTATION_VALUES, ClockSettings.ORIENTATION, "auto");
        section(t("界面", "Interface"));
        TextView transparencyInfo = label("", 16);
        content.addView(transparencyInfo);
        SeekBar transparency = new SeekBar(host);
        transparency.setMax(80);
        transparency.setProgress(Math.max(0, Math.min(80,
                prefs.getInt(ClockSettings.PANEL_TRANSPARENCY, 5))));
        transparencyInfo.setText(t("侧栏背景透明度：", "Panel transparency: ") + transparency.getProgress() + "%");
        transparency.setContentDescription(t("侧栏背景透明度，0% 到 80%", "Panel transparency, 0 to 80 percent"));
        tintSlider(transparency);
        protectSlider(transparency);
        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                transparencyInfo.setText(t("侧栏背景透明度：", "Panel transparency: ") + progress + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.PANEL_TRANSPARENCY, progress).apply();
                    host.onSettingChanged(ClockSettings.PANEL_TRANSPARENCY);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(transparency, new LinearLayout.LayoutParams(-1, dp(48)));
        hint(t("仅调整左右侧栏背景；文字和时钟画面不变。", "Only the panels change; the clock and image stay the same."));
        return panel;
    }

    ScrollView createCustomizationPanel() {
        ScrollView panel = createPanel(t("自定义", "Customize"));
        hint(t("向右滑动或点击面板外侧关闭", "Swipe right or tap outside to close"));
        section(t("背景", "Background"));
        spinner(t("背景适配", "Image layout"),
                new String[]{t("填充", "Fill"), t("适应", "Fit"), t("拉伸", "Stretch"), t("平铺", "Tile")},
                MODE_VALUES, ClockSettings.BACKGROUND_MODE, "fill");
        hint(t("平铺适用于图片；视频选择平铺时按适应显示。",
                "Tile applies to images; videos use Fit when Tile is selected."));
        brightnessInfo = label("", 16);
        content.addView(brightnessInfo);
        SeekBar brightness = new SeekBar(host);
        brightness.setMax(70);
        brightness.setProgress(Math.max(0, Math.min(70, prefs.getInt(ClockSettings.BACKGROUND_DIM, 0))));
        brightnessInfo.setText(t("背景亮度：", "Background brightness: ") + (100 - brightness.getProgress()) + "%");
        brightness.setContentDescription(t("背景亮度，100% 为原图亮度，最低 30%", "Background brightness, 30 to 100 percent"));
        tintSlider(brightness);
        protectSlider(brightness);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightnessInfo.setText(t("背景亮度：", "Background brightness: ") + (100 - progress) + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.BACKGROUND_DIM, progress).apply();
                    host.onSettingChanged(ClockSettings.BACKGROUND_DIM);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(brightness, new LinearLayout.LayoutParams(-1, dp(48)));
        hint(t("100% 为原图亮度；向右拖动可降低背景亮度。",
                "100% is the original image brightness; drag right to dim."));
        backgroundInfo = label("", 14);
        content.addView(backgroundInfo);
        LinearLayout mediaButtons = new LinearLayout(host);
        mediaButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button imageButton = createButton(t("选择图片", "Choose image"), v -> pickImage());
        Button videoButton = createButton(t("选择视频", "Choose video"), v -> pickVideo());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        imageParams.setMargins(0, dp(6), dp(3), 0);
        videoParams.setMargins(dp(3), dp(6), 0, 0);
        mediaButtons.addView(imageButton, imageParams);
        mediaButtons.addView(videoButton, videoParams);
        content.addView(mediaButtons);
        button(t("预览并调整背景位置", "Preview and position image"), v -> host.startBackgroundPreview());
        button(t("恢复默认背景", "Reset background"), v -> {
            prefs.edit().remove(ClockSettings.BACKGROUND_URI).remove(ClockSettings.BACKGROUND_TYPE)
                    .remove(ClockSettings.BACKGROUND_SCALE).remove(ClockSettings.BACKGROUND_PAN_X)
                    .remove(ClockSettings.BACKGROUND_PAN_Y).apply();
            updateInfo();
            host.onSettingChanged(ClockSettings.BACKGROUND_URI);
        });
        section(t("字体", "Font"));
        createFontMenu();
        toggle(t("文字加粗", "Bold text"), ClockSettings.FONT_BOLD, false);
        toggle(t("文字阴影", "Text shadow"), ClockSettings.TEXT_SHADOW, true);
        createFontColorControls();
        fontInfo = label("", 14);
        content.addView(fontInfo);
        button(t("导入 TTF / OTF 字体", "Import TTF / OTF font"), v -> pickFont());
        button(t("恢复默认字体", "Reset font"), v -> {
            prefs.edit().putString(ClockSettings.FONT, "system").remove(ClockSettings.FONT_FILE).apply();
            updateFontMenuLabel();
            updateInfo();
            host.onSettingChanged(ClockSettings.FONT);
        });
        hint(t("视频背景静音循环播放；长时间亮屏可能增加耗电。",
                "Videos play silently on a loop; keeping the screen on uses power."));
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
        panel.setContentDescription(title + t("面板", " panel"));
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
            String name = "SYSTEM".equals(id) ? t("跟随系统", "Follow system")
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

    private void createFontMenu() {
        content.addView(label(t("字体样式", "Font style"), 16));
        fontMenuLabel = label("", 16);
        fontMenuLabel.setGravity(Gravity.CENTER_VERTICAL);
        fontMenuLabel.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x66374759);
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), 0xFF8996A8);
        fontMenuLabel.setBackground(background);
        fontMenuLabel.setOnClickListener(v -> showFontMenu());
        content.addView(fontMenuLabel, new LinearLayout.LayoutParams(-1, dp(48)));
        updateFontMenuLabel();
    }

    private void showFontMenu() {
        if (fontPopup != null && fontPopup.isShowing()) {
            fontPopup.dismiss();
            return;
        }
        LinearLayout choices = new LinearLayout(host);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setBackgroundColor(0xFF263448);
        String[] fontLabels = fontLabels();
        for (int i = 0; i < FONT_VALUES.length; i++) {
            addFontChoice(choices, fontLabels[i], FONT_VALUES[i], null);
        }
        List<FontLibrary.Entry> imported = fonts.entries();
        for (FontLibrary.Entry entry : imported) {
            addFontChoice(choices, entry.name, entry.value(), entry);
        }
        ScrollView scroll = new ScrollView(host);
        scroll.addView(choices);
        int height = Math.min(dp(340), dp(48) * (FONT_VALUES.length + imported.size()));
        fontPopup = new PopupWindow(scroll, fontMenuLabel.getWidth(), height, true);
        fontPopup.setBackgroundDrawable(new ColorDrawable(0xFF263448));
        fontPopup.setOutsideTouchable(true);
        fontPopup.setElevation(dp(12));
        fontPopup.showAsDropDown(fontMenuLabel);
    }

    private void addFontChoice(LinearLayout choices, String name, String value, FontLibrary.Entry entry) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(name, 16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (value.equals(prefs.getString(ClockSettings.FONT, "system"))) {
            title.setTextColor(UiPalette.accent(host));
        }
        title.setPadding(dp(12), 0, dp(4), 0);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.setOnClickListener(v -> {
            prefs.edit().putString(ClockSettings.FONT, value).apply();
            if (fontPopup != null) fontPopup.dismiss();
            updateFontMenuLabel();
            updateInfo();
            host.onSettingChanged(ClockSettings.FONT);
        });
        if (entry != null) {
            TextView remove = label("×", 18);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription(t("删除字体 ", "Delete font ") + name);
            remove.setOnClickListener(v -> {
                if (fonts.remove(entry)) {
                    if (fontPopup != null) fontPopup.dismiss();
                    updateFontMenuLabel();
                    updateInfo();
                    host.onSettingChanged(ClockSettings.FONT);
                    toast(t("已删除：", "Deleted: ") + name);
                } else {
                    toast(t("删除字体失败", "Could not delete font"));
                }
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(42), dp(48)));
        }
        choices.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private String selectedFontName() {
        String selected = prefs.getString(ClockSettings.FONT, "system");
        String[] labels = fontLabels();
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (FONT_VALUES[i].equals(selected)) return labels[i];
        }
        FontLibrary.Entry entry = fonts.find(selected);
        return entry == null ? t("默认字体", "Default font") : entry.name;
    }

    private String[] fontLabels() {
        return new String[]{t("默认字体", "Default font"), t("轻体无衬线", "Light sans serif"),
                t("衬线", "Serif"), t("等宽", "Monospace")};
    }

    private void updateFontMenuLabel() {
        if (fontMenuLabel != null) fontMenuLabel.setText(selectedFontName() + "  ▾");
    }

    private void createFontColorControls() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        colorHeaderLabel = label(t("字体颜色 ▸", "Text color ▸"), 18);
        header.addView(colorHeaderLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        colorPreview = new View(host);
        LinearLayout.LayoutParams swatch = new LinearLayout.LayoutParams(dp(24), dp(24));
        swatch.rightMargin = dp(8);
        header.addView(colorPreview, swatch);
        content.addView(header);
        colorControls = new LinearLayout(host);
        colorControls.setOrientation(LinearLayout.VERTICAL);
        colorControls.setVisibility(View.GONE);
        content.addView(colorControls);
        header.setOnClickListener(v -> {
            boolean open = colorControls.getVisibility() != View.VISIBLE;
            colorControls.setVisibility(open ? View.VISIBLE : View.GONE);
            colorHeaderLabel.setText(open ? t("字体颜色 ▾", "Text color ▾")
                    : t("字体颜色 ▸", "Text color ▸"));
        });
        String[] colorLabels = colorLabels();
        for (int i = 0; i < COLOR_KEYS.length; i++) {
            addColorSlider(colorLabels[i], COLOR_KEYS[i], i == 2 ? 360 : 100,
                    i == 0 || i == 1 ? 100 : 0);
        }
        refreshColorTracks();
        refreshColorThumbs();
    }

    private String[] colorLabels() {
        return new String[]{t("透明度：", "Opacity:"), t("颜色强度：", "Intensity:"),
                t("颜色：", "Hue:"), t("饱和：", "Saturation:")};
    }

    private void addColorSlider(String title, String key, int maximum, int fallback) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(title, 14);
        row.addView(titleView, new LinearLayout.LayoutParams(dp(88), dp(44)));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar slider = new SeekBar(host);
        slider.setMax(maximum);
        slider.setProgress(Math.max(0, Math.min(maximum, prefs.getInt(key, fallback))));
        slider.setPadding(dp(7), 0, dp(7), 0);
        slider.setSplitTrack(false);
        slider.setProgressTintList(null);
        slider.setProgressBackgroundTintList(null);
        slider.setContentDescription(title + t("滑条", " slider"));
        protectSlider(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefs.edit().putInt(key, progress).apply();
                    refreshColorTracks();
                    host.onSettingChanged(key);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(44), 1f));
        colorControls.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
        colorSliders.add(slider);
    }

    private void refreshColorTracks() {
        if (colorSliders.size() != 4) return;
        int hue = Math.max(0, Math.min(360, prefs.getInt(ClockSettings.FONT_HUE, 0)));
        float saturation = Math.max(0, Math.min(100,
                prefs.getInt(ClockSettings.FONT_SATURATION, 0))) / 100f;
        float intensity = Math.max(0, Math.min(100,
                prefs.getInt(ClockSettings.FONT_INTENSITY, 100))) / 100f;
        int solid = Color.HSVToColor(new float[]{hue, saturation, intensity});
        setColorTrack(colorSliders.get(0), new int[]{Color.TRANSPARENT, solid});
        setColorTrack(colorSliders.get(1), new int[]{Color.BLACK,
                Color.HSVToColor(new float[]{hue, saturation, 1f})});
        setColorTrack(colorSliders.get(2), new int[]{Color.RED, Color.YELLOW, Color.GREEN,
                Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
        setColorTrack(colorSliders.get(3), new int[]{
                Color.HSVToColor(new float[]{hue, 0f, intensity}),
                Color.HSVToColor(new float[]{hue, 1f, intensity})});
        if (colorPreview != null) {
            GradientDrawable swatch = new GradientDrawable();
            swatch.setColor(ClockSettings.fontColor(prefs));
            swatch.setCornerRadius(dp(4));
            swatch.setStroke(dp(1), Color.WHITE);
            colorPreview.setBackground(swatch);
        }
    }

    private void refreshColorThumbs() {
        int accent = UiPalette.accent(host);
        for (SeekBar slider : colorSliders) {
            GradientDrawable thumb = new GradientDrawable();
            thumb.setColor(accent);
            thumb.setCornerRadius(dp(4));
            thumb.setSize(dp(12), dp(22));
            slider.setThumb(thumb);
            slider.setThumbTintList(null);
        }
    }

    private void setColorTrack(SeekBar slider, int[] colors) {
        GradientDrawable track = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        track.setCornerRadius(dp(3));
        track.setSize(dp(1), dp(5));
        slider.setProgressDrawable(track);
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
                    toast(t("请选择图片文件", "Choose an image file"));
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
                toast(t("图片导入失败，请重试", "Image import failed; try again"));
            }
        } else if (request == PICK_VIDEO) {
            try {
                String mime = host.getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("video/")) {
                    toast(t("请选择视频文件", "Choose a video file"));
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
                toast(t("无法保存视频文件的访问权限", "Could not keep access to the video"));
            }
        } else if (request == PICK_FONT) {
            String name = displayName(uri);
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
                toast(t("请选择 TTF 或 OTF 字体", "Choose a TTF or OTF font"));
                return;
            }
            try {
                FontLibrary.Entry imported = fonts.importFont(uri, name);
                updateFontMenuLabel();
                updateInfo();
                host.onSettingChanged(ClockSettings.FONT);
                toast(t("已导入：", "Imported: ") + imported.name);
            } catch (Exception error) {
                toast(t("字体导入失败：请检查格式与大小", "Font import failed; check its format and size"));
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
        backgroundInfo.setText(uri.isEmpty() ? t("当前：深色纯色背景", "Current: dark solid background")
                : t("当前：", "Current: ")
                + ("video".equals(prefs.getString(ClockSettings.BACKGROUND_TYPE, "image"))
                ? t("视频", "video") : t("图片", "image")) + t("文件", " file"));
        fontInfo.setText(t("当前字体：", "Current font: ") + selectedFontName());
    }

    private void toast(String text) { Toast.makeText(host, text, Toast.LENGTH_SHORT).show(); }
    private String t(String chinese, String english) { return L10n.text(host, chinese, english); }
    private int dp(int value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }
}
