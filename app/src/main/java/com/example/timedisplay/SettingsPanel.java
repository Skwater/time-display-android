package com.example.timedisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.DragEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SettingsPanel {
    private static final String SOURCE_URL = "https://github.com/Skwater/time-display-android";
    private static final String LICENSE_URL = SOURCE_URL + "/blob/main/LICENSE";
    private static final int PICK_IMAGE = 1;
    private static final int PICK_FONT = 2;
    private static final int PICK_VIDEO = 3;
    private static final int PICK_PLAYLIST_IMAGES = 4;
    private static final int PICK_PLAYLIST_VIDEOS = 5;
    private static final int PICK_PLAYLIST_FOLDER = 6;
    private static final int PLAYLIST_PAGE_SIZE = 50;
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
    private static final String[] VIDEO_MODE_VALUES = {"fill", "fit", "stretch"};
    private static final String[] SOURCE_VALUES = {"single", "playlist"};
    private static final String[] LANGUAGE_VALUES = {"system", "zh", "en"};
    private static final String[] TIME_FORMAT_VALUES = {"12", "24"};
    private static final String[] FONT_VALUES = {"system", "sans", "serif", "mono"};
    private static final String[] COLOR_KEYS = {ClockSettings.FONT_OPACITY,
            ClockSettings.FONT_INTENSITY, ClockSettings.FONT_HUE, ClockSettings.FONT_SATURATION};
    private static final String[] DEFAULT_BACKGROUND_COLOR_KEYS = {
            ClockSettings.DEFAULT_BACKGROUND_OPACITY, ClockSettings.DEFAULT_BACKGROUND_INTENSITY,
            ClockSettings.DEFAULT_BACKGROUND_HUE, ClockSettings.DEFAULT_BACKGROUND_SATURATION};

    private final MainActivity host;
    private final SharedPreferences prefs;
    private final FontLibrary fonts;
    private final PlaylistStore playlist;
    private final List<Button> buttons = new ArrayList<>();
    private final List<SeekBar> sliders = new ArrayList<>();
    private final List<Switch> switches = new ArrayList<>();
    private final List<SeekBar> colorSliders = new ArrayList<>();
    private final List<SeekBar> defaultBackgroundColorSliders = new ArrayList<>();
    private LinearLayout content;
    private TextView backgroundInfo;
    private TextView brightnessInfo;
    private TextView fontInfo;
    private TextView fontMenuLabel;
    private Button selectedZoneButton;
    private Switch amPmToggle;
    private TextView colorHeaderLabel;
    private View colorPreview;
    private View defaultBackgroundColorPreview;
    private LinearLayout colorControls;
    private LinearLayout defaultBackgroundColorControls;
    private PopupWindow fontPopup;
    private ScrollView customizationPanel;
    private LinearLayout customizationHome;
    private LinearLayout playlistPage;
    private LinearLayout playlistList;
    private TextView playlistInfo;
    private TextView playlistPageInfo;
    private Spinner savedPlaylistSpinner;
    private int playlistPageIndex;
    private boolean refreshingSavedLists;
    private Spinner backgroundSourceSpinner;
    private LinearLayout singleBackgroundControls;
    private Button folderButton;
    private Button playlistPauseButton;
    private boolean folderImporting;

    SettingsPanel(MainActivity host) {
        this.host = host;
        this.prefs = ClockSettings.of(host);
        this.fonts = new FontLibrary(host, prefs);
        this.playlist = new PlaylistStore(host);
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
        refreshDefaultBackgroundColorTracks();
    }

    ScrollView createSettingsPanel() {
        ScrollView panel = createPanel(t("设置", "Settings"));
        hint(t("向左滑动或点击面板外侧关闭", "Swipe left or tap outside to close"));
        section(t("语言", "Language"));
        spinner(t("应用语言", "App language"),
                new String[]{t("跟随系统", "Follow system"), "中文", "English"},
                LANGUAGE_VALUES, ClockSettings.LANGUAGE, "system");
        section(t("时间与日期", "Time and date"));
        spinner(t("时间格式", "Time format"),
                new String[]{t("12 小时制", "12-hour"),
                        t("24 小时制", "24-hour")},
                TIME_FORMAT_VALUES, ClockSettings.TIME_FORMAT, "12");
        amPmToggle = toggle(t("显示 AM/PM", "Show AM/PM"), ClockSettings.SHOW_AM_PM, true);
        updateAmPmToggleVisibility();
        content.addView(label(t("时区", "Time zone"), 16));
        selectedZoneButton = createButton("", v -> showZonePicker());
        selectedZoneButton.setSingleLine(true);
        selectedZoneButton.setEllipsize(TextUtils.TruncateAt.END);
        refreshSelectedZone();
        LinearLayout.LayoutParams zoneParams = new LinearLayout.LayoutParams(-1, dp(52));
        zoneParams.topMargin = dp(6);
        content.addView(selectedZoneButton, zoneParams);
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
        section(t("关于", "About"));
        hint(t("版本：", "Version: ") + appVersion());
        infoLink(t("开源地址", "Source code"), SOURCE_URL);
        hint(t("开源协议：GNU 通用公共许可证第 3 版（GPLv3）",
                "License: GNU General Public License version 3 (GPLv3)"));
        infoLink(t("查看完整协议", "Read the full license"), LICENSE_URL);
        return panel;
    }

    ScrollView createCustomizationPanel() {
        ScrollView panel = createPanel(t("自定义", "Customize"));
        customizationPanel = panel;
        hint(t("向右滑动或点击面板外侧关闭", "Swipe right or tap outside to close"));
        section(t("背景", "Background"));
        createDefaultBackgroundColorControls();
        backgroundSourceSpinner = spinner(t("背景来源", "Background source"),
                new String[]{t("单个背景", "Single background"), t("播放列表", "Playlist")},
                SOURCE_VALUES, ClockSettings.BACKGROUND_SOURCE, "single");
        LinearLayout backgroundRoot = content;
        singleBackgroundControls = new LinearLayout(host);
        singleBackgroundControls.setOrientation(LinearLayout.VERTICAL);
        backgroundRoot.addView(singleBackgroundControls);
        content = singleBackgroundControls;
        section(t("单个背景", "Single background"));
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
        content = backgroundRoot;
        updateSingleBackgroundControls();
        button(t("管理播放列表", "Manage playlist"), v -> showPlaylistPage());
        section(t("字体", "Font"));
        createFontMenu();
        createFontSizeControls();
        button(t("预览并调整文字位置", "Preview and position text"),
                v -> host.startTextPositionPreview());
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
        createPlaylistPage();
        return panel;
    }

    boolean onBackPressed() {
        if (playlistPage != null && playlistPage.getVisibility() == View.VISIBLE) {
            showCustomizationHome();
            return true;
        }
        return false;
    }

    private void showPlaylistPage() {
        customizationHome.setVisibility(View.GONE);
        playlistPage.setVisibility(View.VISIBLE);
        refreshSavedLists();
        refreshPlaylistList();
        refreshPlaylistPauseButton();
        customizationPanel.scrollTo(0, 0);
    }

    private void showCustomizationHome() {
        playlistPage.setVisibility(View.GONE);
        customizationHome.setVisibility(View.VISIBLE);
        customizationPanel.scrollTo(0, 0);
    }

    private void updateSingleBackgroundControls() {
        if (singleBackgroundControls != null) {
            singleBackgroundControls.setVisibility("playlist".equals(prefs.getString(
                    ClockSettings.BACKGROUND_SOURCE, "single")) ? View.GONE : View.VISIBLE);
        }
    }

    private void createPlaylistPage() {
        LinearLayout pageRoot = content;
        customizationHome = new LinearLayout(host);
        customizationHome.setOrientation(LinearLayout.VERTICAL);
        while (pageRoot.getChildCount() > 0) {
            View child = pageRoot.getChildAt(0);
            pageRoot.removeViewAt(0);
            customizationHome.addView(child);
        }
        pageRoot.addView(customizationHome);
        playlistPage = new LinearLayout(host);
        playlistPage.setOrientation(LinearLayout.VERTICAL);
        playlistPage.setVisibility(View.GONE);
        pageRoot.addView(playlistPage);
        content = playlistPage;
        button(t("‹ 返回自定义", "‹ Back to customize"), v -> showCustomizationHome());
        playlistPauseButton = createButton("", v -> {
            host.togglePlaylistPause();
            refreshPlaylistPauseButton();
        });
        content.addView(playlistPauseButton, new LinearLayout.LayoutParams(-1, dp(48)));
        refreshPlaylistPauseButton();
        section(t("播放列表", "Playlist"));
        content.addView(label(t("选择并编辑已保存列表", "Select and edit a saved playlist"), 16));
        savedPlaylistSpinner = new Spinner(host);
        content.addView(savedPlaylistSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        savedPlaylistSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
                if (refreshingSavedLists) return;
                if (folderImporting) { refreshSavedLists(); return; }
                List<PlaylistStore.SavedList> lists = playlist.savedLists();
                if (position >= 0 && position < lists.size() && playlist.activeId() != lists.get(position).id
                        && playlist.select(lists.get(position).id)) {
                    playlistPageIndex = 0;
                    playlistChanged();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        LinearLayout listButtons = new LinearLayout(host);
        listButtons.setOrientation(LinearLayout.HORIZONTAL);
        addPlaylistManagementButton(listButtons, t("新建", "New"), () -> askPlaylistName("", false));
        addPlaylistManagementButton(listButtons, t("另存为", "Save as"), () -> askPlaylistName("", true));
        content.addView(listButtons);
        LinearLayout editButtons = new LinearLayout(host);
        editButtons.setOrientation(LinearLayout.HORIZONTAL);
        addPlaylistManagementButton(editButtons, t("重命名", "Rename"), () -> {
            for (PlaylistStore.SavedList list : playlist.savedLists()) {
                if (list.id == playlist.activeId()) { askRenameList(list.name); break; }
            }
        });
        addPlaylistManagementButton(editButtons, t("删除列表", "Delete list"), this::confirmDeleteList);
        content.addView(editButtons);
        button(t("一键清空当前列表", "Clear current playlist"), v -> confirmClearList());
        hint(t("长按项目可拖动排序；也可使用上下箭头。", "Long press to reorder, or use the arrows."));
        playlistInfo = label("", 14);
        content.addView(playlistInfo);
        LinearLayout pageControls = new LinearLayout(host);
        pageControls.setGravity(Gravity.CENTER_VERTICAL);
        addPlaylistManagementButton(pageControls, t("上一页", "Previous"), () -> {
            if (playlistPageIndex > 0) { playlistPageIndex--; refreshPlaylistList(); }
        });
        playlistPageInfo = label("", 13);
        playlistPageInfo.setGravity(Gravity.CENTER);
        pageControls.addView(playlistPageInfo, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addPlaylistManagementButton(pageControls, t("下一页", "Next"), () -> {
            if ((playlistPageIndex + 1) * PLAYLIST_PAGE_SIZE < playlist.count()) {
                playlistPageIndex++; refreshPlaylistList();
            }
        });
        content.addView(pageControls);
        playlistList = new LinearLayout(host);
        playlistList.setOrientation(LinearLayout.VERTICAL);
        content.addView(playlistList);
        LinearLayout addButtons = new LinearLayout(host);
        addButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button addImages = createButton(t("添加图片", "Add images"), v -> pickPlaylistImages());
        Button addVideos = createButton(t("添加视频", "Add videos"), v -> pickPlaylistVideos());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(50), 1f);
        half.setMargins(0, dp(6), dp(3), 0);
        addButtons.addView(addImages, half);
        LinearLayout.LayoutParams otherHalf = new LinearLayout.LayoutParams(0, dp(50), 1f);
        otherHalf.setMargins(dp(3), dp(6), 0, 0);
        addButtons.addView(addVideos, otherHalf);
        content.addView(addButtons);
        folderButton = createButton(t("添加文件夹", "Add folder"), v -> pickPlaylistFolder());
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(-1, dp(50));
        folderParams.topMargin = dp(6);
        content.addView(folderButton, folderParams);
        section(t("播放设置", "Playback settings"));
        TextView playlistBrightnessInfo = label("", 16);
        content.addView(playlistBrightnessInfo);
        SeekBar playlistBrightness = new SeekBar(host);
        playlistBrightness.setMax(70);
        playlistBrightness.setProgress(Math.max(0, Math.min(70,
                prefs.getInt(ClockSettings.PLAYLIST_DIM, 0))));
        playlistBrightnessInfo.setText(t("列表背景亮度：", "Playlist brightness: ")
                + (100 - playlistBrightness.getProgress()) + "%");
        tintSlider(playlistBrightness);
        protectSlider(playlistBrightness);
        playlistBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                playlistBrightnessInfo.setText(t("列表背景亮度：", "Playlist brightness: ")
                        + (100 - progress) + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.PLAYLIST_DIM, progress).apply();
                    host.onSettingChanged(ClockSettings.PLAYLIST_DIM);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(playlistBrightness, new LinearLayout.LayoutParams(-1, dp(48)));
        spinner(t("图片和动图显示模式", "Image and animation layout"),
                new String[]{t("填充", "Fill"), t("适应", "Fit"), t("拉伸", "Stretch"), t("平铺", "Tile")},
                MODE_VALUES, ClockSettings.PLAYLIST_IMAGE_MODE, "fill");
        spinner(t("视频显示模式", "Video layout"),
                new String[]{t("填充", "Fill"), t("适应", "Fit"), t("拉伸", "Stretch")},
                VIDEO_MODE_VALUES, ClockSettings.PLAYLIST_VIDEO_MODE, "fill");
        toggle(t("切换时交叠淡化", "Crossfade between items"), ClockSettings.PLAYLIST_FADE, true);
        toggle(t("随机顺序", "Shuffle"), ClockSettings.PLAYLIST_SHUFFLE, false);
        toggle(t("循环播放", "Loop playlist"), ClockSettings.PLAYLIST_LOOP, true);
        TextView intervalLabel = label("", 16);
        content.addView(intervalLabel);
        SeekBar interval = new SeekBar(host);
        interval.setMax(57);
        interval.setProgress(Math.max(3, Math.min(60,
                prefs.getInt(ClockSettings.PLAYLIST_INTERVAL, 10))) - 3);
        intervalLabel.setText(t("图片停留时间：", "Image duration: ")
                + (interval.getProgress() + 3) + t(" 秒", " s"));
        tintSlider(interval);
        protectSlider(interval);
        interval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int seconds = progress + 3;
                intervalLabel.setText(t("图片停留时间：", "Image duration: ")
                        + seconds + t(" 秒", " s"));
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.PLAYLIST_INTERVAL, seconds).apply();
                    host.onSettingChanged(ClockSettings.PLAYLIST_INTERVAL);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(interval, new LinearLayout.LayoutParams(-1, dp(48)));
        hint(t("图片和动图按停留时间切换；视频播完后切换。填充模式居中裁切。",
                "Images and animations use this duration; videos advance when they end. Fill crops from center."));
        content = pageRoot;
        refreshSavedLists();
        refreshPlaylistList();
    }

    private void addPlaylistManagementButton(LinearLayout row, String title, Runnable action) {
        Button control = createButton(title, v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(2), dp(4), dp(2), 0);
        row.addView(control, params);
    }

    private void refreshSavedLists() {
        if (savedPlaylistSpinner == null) return;
        List<PlaylistStore.SavedList> lists = playlist.savedLists();
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < lists.size(); i++) {
            names.add(lists.get(i).name);
            if (lists.get(i).id == playlist.activeId()) selected = i;
        }
        refreshingSavedLists = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                                                   android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.BLACK);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        savedPlaylistSpinner.setAdapter(adapter);
        savedPlaylistSpinner.setSelection(selected);
        savedPlaylistSpinner.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        savedPlaylistSpinner.post(() -> refreshingSavedLists = false);
    }

    void refreshPlaylistPauseButton() {
        if (playlistPauseButton != null) {
            playlistPauseButton.setText(host.isPlaylistPaused()
                    ? t("继续播放", "Resume playlist") : t("暂停播放", "Pause playlist"));
            playlistPauseButton.setEnabled(host.isPlaylistActive());
        }
    }

    private void askPlaylistName(String initial, boolean copy) {
        if (folderImporting) return;
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(host)
                .setTitle(copy ? t("另存播放列表", "Save playlist as") : t("新建播放列表", "New playlist"))
                .setView(input)
                .setNegativeButton(t("取消", "Cancel"), null)
                .setPositiveButton(t("保存", "Save"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { toast(t("请输入名称", "Enter a name")); return; }
                    long id = copy ? playlist.saveCopy(name) : playlist.createEmpty(name);
                    if (id < 0) { toast(t("保存失败或名称已存在", "Could not save; name may exist")); return; }
                    playlist.select(id);
                    playlistPageIndex = 0;
                    refreshSavedLists();
                    playlistChanged();
                }).show();
    }

    private void askRenameList(String initial) {
        if (folderImporting) return;
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setText(initial);
        input.selectAll();
        new AlertDialog.Builder(host).setTitle(t("重命名播放列表", "Rename playlist"))
                .setView(input).setNegativeButton(t("取消", "Cancel"), null)
                .setPositiveButton(t("保存", "Save"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || !playlist.rename(playlist.activeId(), name)) {
                        toast(t("重命名失败或名称已存在", "Could not rename; name may exist"));
                        return;
                    }
                    refreshSavedLists();
                }).show();
    }

    private void confirmDeleteList() {
        if (folderImporting) return;
        new AlertDialog.Builder(host).setMessage(t("删除当前播放列表及其中的项目？", "Delete this playlist and its items?"))
                .setNegativeButton(t("取消", "Cancel"), null)
                .setPositiveButton(t("删除", "Delete"), (dialog, which) -> {
                    if (playlist.deleteList(playlist.activeId())) {
                        playlistPageIndex = 0;
                        refreshSavedLists();
                        playlistChanged();
                    } else toast(t("删除失败", "Could not delete playlist"));
                }).show();
    }

    private void confirmClearList() {
        if (folderImporting) return;
        new AlertDialog.Builder(host).setMessage(t("清空当前播放列表中的所有项目？", "Clear all items in this playlist?"))
                .setNegativeButton(t("取消", "Cancel"), null)
                .setPositiveButton(t("清空", "Clear"), (dialog, which) -> {
                    playlist.clearActive();
                    playlistPageIndex = 0;
                    playlistChanged();
                }).show();
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

    private void refreshSelectedZone() {
        String id = prefs.getString(ClockSettings.ZONE, "SYSTEM");
        selectedZoneButton.setText(t("时区：", "Zone: ")
                + zoneLabel(id, Instant.now()).split(" · ")[0]);
    }

    private void showZonePicker() {
        LinkedHashSet<String> zoneIds = new LinkedHashSet<>();
        Collections.addAll(zoneIds, ZONES);
        List<String> remaining = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(remaining);
        zoneIds.addAll(remaining);
        List<String> allIds = new ArrayList<>(zoneIds);
        List<String> allLabels = new ArrayList<>(allIds.size());
        Instant now = Instant.now();
        for (String id : allIds) allLabels.add(zoneLabel(id, now));

        LinearLayout layout = new LinearLayout(host);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), 0);
        EditText search = new EditText(host);
        search.setSingleLine(true);
        search.setHint(t("搜索 +8、城市或时区名称", "Search +8, city or zone name"));
        layout.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        ListView results = new ListView(host);
        layout.addView(results, new LinearLayout.LayoutParams(-1,
                Math.min(dp(420), Math.round(host.getResources().getDisplayMetrics().heightPixels * 0.55f))));
        List<String> visibleIds = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(host,
                android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setSingleLine(false);
                row.setMaxLines(2);
                row.setEllipsize(TextUtils.TruncateAt.END);
                return row;
            }
        };
        results.setAdapter(adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
            visibleIds.clear();
            adapter.clear();
            List<String> visibleLabels = new ArrayList<>();
            for (int i = 0; i < allIds.size(); i++) {
                String searchable = allLabels.get(i).toLowerCase(Locale.ROOT);
                String readable = searchable.replace('_', ' ');
                boolean matches = true;
                for (String term : terms) {
                    if (!searchable.contains(term) && !readable.contains(term)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    visibleIds.add(allIds.get(i));
                    visibleLabels.add(allLabels.get(i));
                }
            }
            adapter.addAll(visibleLabels);
            results.setSelection(0);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filter.run();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        filter.run();
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(t("选择时区", "Choose time zone"))
                .setView(layout)
                .setNegativeButton(t("取消", "Cancel"), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(UiPalette.accent(host)));
        results.setOnItemClickListener((parent, view, position, id) -> {
            String selected = visibleIds.get(position);
            prefs.edit().putString(ClockSettings.ZONE, selected).apply();
            refreshSelectedZone();
            host.onSettingChanged(ClockSettings.ZONE);
            dialog.dismiss();
        });
        dialog.show();
    }

    private String zoneLabel(String id, Instant now) {
        try {
            ZoneId zone = "SYSTEM".equals(id) ? ZoneId.systemDefault() : ZoneId.of(id);
            ZoneOffset offset = zone.getRules().getOffset(now);
            int minutes = offset.getTotalSeconds() / 60;
            int absolute = Math.abs(minutes);
            String prefix = (minutes < 0 ? "-" : "+") + (absolute / 60)
                    + (absolute % 60 == 0 ? "" : String.format(Locale.ROOT, ":%02d", absolute % 60));
            if ("SYSTEM".equals(id)) {
                return prefix + "  " + t("跟随系统", "Follow system") + " (" + zone.getId() + ")";
            }
            if ("UTC".equals(id)) return prefix + "  UTC";
            String city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
            TimeZone timeZone = TimeZone.getTimeZone(id);
            String localName = timeZone.getDisplayName(timeZone.inDaylightTime(Date.from(now)),
                    TimeZone.LONG, L10n.locale(host));
            return prefix + "  " + city + " (" + id + ")"
                    + (localName.equalsIgnoreCase(city) || localName.equalsIgnoreCase(id)
                    ? "" : " · " + localName);
        } catch (RuntimeException exception) {
            return id;
        }
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

    private Spinner spinner(String title, String[] labels, String[] values, String key, String fallback) {
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
                    if (ClockSettings.BACKGROUND_SOURCE.equals(key)) updateSingleBackgroundControls();
                    if (ClockSettings.TIME_FORMAT.equals(key)) updateAmPmToggleVisibility();
                    host.onSettingChanged(key);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        return spinner;
    }

    private void updateAmPmToggleVisibility() {
        if (amPmToggle != null) amPmToggle.setVisibility(
                "24".equals(prefs.getString(ClockSettings.TIME_FORMAT, "12"))
                        ? View.GONE : View.VISIBLE);
    }

    private Switch toggle(String title, String key, boolean fallback) {
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
        return control;
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

    private void infoLink(String title, String url) {
        TextView view = label(title + "  ↗\n" + url, 14);
        view.setTextColor(0xFFBBD6FF);
        view.setPadding(0, dp(8), 0, dp(8));
        view.setContentDescription(title + " " + url);
        view.setOnClickListener(v -> {
            try {
                host.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException | SecurityException error) {
                toast(t("无法打开链接", "Cannot open link"));
            }
        });
        content.addView(view);
    }

    private String appVersion() {
        try {
            String version = host.getPackageManager()
                    .getPackageInfo(host.getPackageName(), 0).versionName;
            return version == null ? "?" : version;
        } catch (PackageManager.NameNotFoundException error) {
            return "?";
        }
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

    private void createFontSizeControls() {
        int saved = Math.max(50, Math.min(200,
                prefs.getInt(ClockSettings.FONT_SIZE_PERCENT, 100)));
        TextView sizeLabel = label(t("字体大小：", "Font size: ") + saved + "%", 16);
        content.addView(sizeLabel);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar sizeSlider = new SeekBar(host);
        sizeSlider.setMax(150);
        sizeSlider.setProgress(saved - 50);
        sizeSlider.setContentDescription(t("字体大小，50% 到 200%", "Font size, 50 to 200 percent"));
        tintSlider(sizeSlider);
        protectSlider(sizeSlider);
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = progress + 50;
                sizeLabel.setText(t("字体大小：", "Font size: ") + percent + "%");
                if (fromUser) {
                    prefs.edit().putInt(ClockSettings.FONT_SIZE_PERCENT, percent).apply();
                    host.onSettingChanged(ClockSettings.FONT_SIZE_PERCENT);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(sizeSlider, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button reset = createButton(t("重置", "Reset"), v -> {
            sizeSlider.setProgress(50);
            prefs.edit().putInt(ClockSettings.FONT_SIZE_PERCENT, 100).apply();
            host.onSettingChanged(ClockSettings.FONT_SIZE_PERCENT);
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(76), dp(44));
        resetParams.leftMargin = dp(6);
        row.addView(reset, resetParams);
        content.addView(row);
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

    private void createDefaultBackgroundColorControls() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView headerLabel = label(t("默认背景颜色 ▸", "Default background color ▸"), 18);
        header.addView(headerLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        defaultBackgroundColorPreview = new View(host);
        LinearLayout.LayoutParams swatch = new LinearLayout.LayoutParams(dp(24), dp(24));
        swatch.rightMargin = dp(8);
        header.addView(defaultBackgroundColorPreview, swatch);
        content.addView(header);
        defaultBackgroundColorControls = new LinearLayout(host);
        defaultBackgroundColorControls.setOrientation(LinearLayout.VERTICAL);
        defaultBackgroundColorControls.setVisibility(View.GONE);
        content.addView(defaultBackgroundColorControls);
        header.setOnClickListener(v -> {
            boolean open = defaultBackgroundColorControls.getVisibility() != View.VISIBLE;
            defaultBackgroundColorControls.setVisibility(open ? View.VISIBLE : View.GONE);
            headerLabel.setText(open ? t("默认背景颜色 ▾", "Default background color ▾")
                    : t("默认背景颜色 ▸", "Default background color ▸"));
        });
        String[] labels = colorLabels();
        int[] defaults = {100, 13, 219, 63};
        for (int i = 0; i < DEFAULT_BACKGROUND_COLOR_KEYS.length; i++) {
            addColorSlider(labels[i], DEFAULT_BACKGROUND_COLOR_KEYS[i], i == 2 ? 360 : 100,
                    defaults[i], defaultBackgroundColorControls, defaultBackgroundColorSliders,
                    this::refreshDefaultBackgroundColorTracks);
        }
        refreshDefaultBackgroundColorTracks();
        refreshColorThumbs();
    }

    private String[] colorLabels() {
        return new String[]{t("透明度：", "Opacity:"), t("颜色强度：", "Intensity:"),
                t("颜色：", "Hue:"), t("饱和：", "Saturation:")};
    }

    private void addColorSlider(String title, String key, int maximum, int fallback) {
        addColorSlider(title, key, maximum, fallback, colorControls, colorSliders,
                this::refreshColorTracks);
    }

    private void addColorSlider(String title, String key, int maximum, int fallback,
                                LinearLayout destination, List<SeekBar> group, Runnable refresh) {
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
                    refresh.run();
                    host.onSettingChanged(key);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(44), 1f));
        destination.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
        group.add(slider);
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

    private void refreshDefaultBackgroundColorTracks() {
        if (defaultBackgroundColorSliders.size() != 4) return;
        int hue = Math.max(0, Math.min(360,
                prefs.getInt(ClockSettings.DEFAULT_BACKGROUND_HUE, 219)));
        float saturation = Math.max(0, Math.min(100,
                prefs.getInt(ClockSettings.DEFAULT_BACKGROUND_SATURATION, 63))) / 100f;
        float intensity = Math.max(0, Math.min(100,
                prefs.getInt(ClockSettings.DEFAULT_BACKGROUND_INTENSITY, 13))) / 100f;
        int solid = Color.HSVToColor(new float[]{hue, saturation, intensity});
        setColorTrack(defaultBackgroundColorSliders.get(0), new int[]{Color.TRANSPARENT, solid});
        setColorTrack(defaultBackgroundColorSliders.get(1), new int[]{Color.BLACK,
                Color.HSVToColor(new float[]{hue, saturation, 1f})});
        setColorTrack(defaultBackgroundColorSliders.get(2), new int[]{Color.RED, Color.YELLOW,
                Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
        setColorTrack(defaultBackgroundColorSliders.get(3), new int[]{
                Color.HSVToColor(new float[]{hue, 0f, intensity}),
                Color.HSVToColor(new float[]{hue, 1f, intensity})});
        if (defaultBackgroundColorPreview != null) {
            GradientDrawable swatch = new GradientDrawable();
            swatch.setColor(ClockSettings.defaultBackgroundColor(prefs));
            swatch.setCornerRadius(dp(4));
            swatch.setStroke(dp(1), Color.WHITE);
            defaultBackgroundColorPreview.setBackground(swatch);
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
        for (SeekBar slider : defaultBackgroundColorSliders) {
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

    private void refreshPlaylistList() {
        if (playlistList == null) return;
        int total = playlist.count();
        int pageCount = Math.max(1, (total + PLAYLIST_PAGE_SIZE - 1) / PLAYLIST_PAGE_SIZE);
        playlistPageIndex = Math.max(0, Math.min(playlistPageIndex, pageCount - 1));
        int offset = playlistPageIndex * PLAYLIST_PAGE_SIZE;
        List<PlaylistStore.Entry> items = playlist.entries(offset, PLAYLIST_PAGE_SIZE);
        playlistInfo.setText(t("共 ", "Total: ") + total + t(" 项", " items"));
        playlistPageInfo.setText((playlistPageIndex + 1) + " / " + pageCount);
        playlistList.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = label(t("列表为空，请添加图片或视频。", "The list is empty. Add images or videos."), 14);
            empty.setPadding(0, dp(12), 0, dp(12));
            playlistList.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            PlaylistStore.Entry entry = items.get(i);
            int position = offset + i;
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            row.setBackgroundColor(i % 2 == 0 ? 0x332F435D : 0x222F435D);
            if ("image".equals(entry.type)) {
                ImageView thumbnail = new ImageView(host);
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                android.graphics.Bitmap preview = playlist.thumbnail(entry);
                if (preview != null) thumbnail.setImageBitmap(preview);
                else thumbnail.setBackgroundColor(0xFF52647A);
                row.addView(thumbnail, new LinearLayout.LayoutParams(dp(44), dp(44)));
            } else {
                TextView videoIcon = label("▶", 21);
                videoIcon.setGravity(Gravity.CENTER);
                videoIcon.setBackgroundColor(0xFF52647A);
                row.addView(videoIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            }
            TextView name = label(entry.name, 14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(dp(8), 0, dp(2), 0);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1f));
            row.addView(playlistAction("↑", t("上移 ", "Move up ") + entry.name, () -> {
                if (!folderImporting && playlist.move(entry.id, -1)) playlistChanged();
            }), new LinearLayout.LayoutParams(dp(26), dp(48)));
            row.addView(playlistAction("↓", t("下移 ", "Move down ") + entry.name, () -> {
                if (!folderImporting && playlist.move(entry.id, 1)) playlistChanged();
            }), new LinearLayout.LayoutParams(dp(26), dp(48)));
            row.addView(playlistAction("×", t("删除 ", "Delete ") + entry.name, () -> {
                if (folderImporting) return;
                if (playlist.remove(entry.id)) playlistChanged();
                else toast(t("删除失败", "Could not delete item"));
            }), new LinearLayout.LayoutParams(dp(30), dp(48)));
            row.setOnLongClickListener(v -> {
                if (folderImporting) return false;
                ClipData data = ClipData.newPlainText("playlist-entry", entry.id);
                return v.startDragAndDrop(data, new View.DragShadowBuilder(v), null, 0);
            });
            row.setOnClickListener(v -> {
                if (folderImporting) return;
                prefs.edit().putString(ClockSettings.BACKGROUND_SOURCE, "playlist").apply();
                updateSingleBackgroundControls();
                if (backgroundSourceSpinner != null) backgroundSourceSpinner.setSelection(1);
                host.playPlaylistEntry(entry.id);
            });
            row.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) return true;
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    if (folderImporting) return true;
                    CharSequence dragged = event.getClipData().getItemAt(0).getText();
                    if (dragged != null && playlist.moveTo(dragged.toString(), position)) playlistChanged();
                    return true;
                }
                return true;
            });
            playlistList.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
        }
    }

    private TextView playlistAction(String text, String description, Runnable click) {
        TextView action = label(text, 20);
        action.setGravity(Gravity.CENTER);
        action.setTextColor(UiPalette.accent(host));
        action.setContentDescription(description);
        action.setOnClickListener(v -> click.run());
        return action;
    }

    private void playlistChanged() {
        refreshPlaylistList();
        host.onSettingChanged(ClockSettings.PLAYLIST_ITEMS);
    }

    private void pickPlaylistImages() {
        if (folderImporting) return;
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                    Math.min(20, MediaStore.getPickImagesMaxLimit()));
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            host.startActivityForResult(intent, PICK_PLAYLIST_IMAGES);
        } catch (ActivityNotFoundException unavailable) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            fallback.setType("image/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            host.startActivityForResult(fallback, PICK_PLAYLIST_IMAGES);
        }
    }

    private void pickPlaylistVideos() {
        if (folderImporting) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        host.startActivityForResult(intent, PICK_PLAYLIST_VIDEOS);
    }

    private void pickPlaylistFolder() {
        if (folderImporting) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            host.startActivityForResult(intent, PICK_PLAYLIST_FOLDER);
        } catch (ActivityNotFoundException unavailable) {
            toast(t("此设备没有文件夹选择器", "No folder picker is available"));
        }
    }

    private void importPlaylistFolder(Uri treeUri) {
        if (folderImporting) return;
        try {
            host.getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            toast(t("无法长期读取所选文件夹", "Could not retain access to this folder"));
            return;
        }
        folderImporting = true;
        folderButton.setEnabled(false);
        savedPlaylistSpinner.setEnabled(false);
        playlistInfo.setText(t("正在扫描文件夹…", "Scanning folder…"));
        android.content.Context appContext = host.getApplicationContext();
        long targetList = playlist.activeId();
        new Thread(() -> {
            PlaylistFolderImporter.Result scan;
            try {
                scan = PlaylistFolderImporter.scanTree(appContext, treeUri);
            } catch (Exception error) {
                scan = null;
            }
            PlaylistFolderImporter.Result finishedScan = scan;
            host.runOnUiThread(() -> {
                if (host.isDestroyed()) return;
                if (finishedScan == null) {
                    finishFolderImport(treeUri, appContext);
                    toast(t("文件夹读取失败", "Could not read the folder"));
                    return;
                }
                confirmHundred(targetList, finishedScan.recognized,
                        () -> startFolderImport(treeUri, appContext, targetList),
                        () -> finishFolderImport(treeUri, appContext));
            });
        }, "playlist-folder-scan").start();
    }

    private void startFolderImport(Uri treeUri, android.content.Context appContext, long targetList) {
        playlistInfo.setText(t("正在导入文件夹…", "Importing folder…"));
        new Thread(() -> {
            PlaylistFolderImporter.Result result;
            PlaylistStore importStore = new PlaylistStore(appContext);
            try {
                result = PlaylistFolderImporter.importTree(appContext, treeUri, importStore, targetList);
            } catch (Exception error) {
                result = null;
            }
            PlaylistFolderImporter.Result finished = result;
            host.runOnUiThread(() -> {
                if (host.isDestroyed()) return;
                finishFolderImport(treeUri, appContext);
                if (finished == null) {
                    toast(t("文件夹导入失败", "Could not import folder"));
                    return;
                }
                if (finished.added > 0) {
                    prefs.edit().putString(ClockSettings.BACKGROUND_SOURCE, "playlist").apply();
                    updateSingleBackgroundControls();
                    if (backgroundSourceSpinner != null) backgroundSourceSpinner.setSelection(1);
                    playlistChanged();
                }
                String message = t("已添加 ", "Added ") + finished.added + t(" 项", " items");
                if (finished.skipped > 0) message += t("；跳过 ", "; skipped ") + finished.skipped;
                if (finished.failed > 0) message += t("；失败 ", "; failed ") + finished.failed;
                Toast.makeText(host, message, Toast.LENGTH_LONG).show();
            });
        }, "playlist-folder-import").start();
    }

    private void finishFolderImport(Uri treeUri, android.content.Context appContext) {
        if (!playlist.usesTree(treeUri)) {
            try {
                appContext.getContentResolver().releasePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
        folderImporting = false;
        folderButton.setEnabled(true);
        savedPlaylistSpinner.setEnabled(true);
        refreshPlaylistList();
    }

    private void confirmHundred(long listId, int incoming, Runnable proceed, Runnable cancel) {
        int current = 0;
        for (PlaylistStore.SavedList list : playlist.savedLists()) {
            if (list.id == listId) { current = list.size; break; }
        }
        if (current < 100 && current + incoming >= 100) {
            AlertDialog dialog = new AlertDialog.Builder(host)
                    .setMessage(t("导入后列表将达到或超过 100 项，继续导入？",
                            "This playlist will reach 100 or more items. Continue importing?"))
                    .setNegativeButton(t("取消", "Cancel"), (d, which) -> cancel.run())
                    .setOnCancelListener(d -> cancel.run())
                    .setPositiveButton(t("继续导入", "Continue"), (d, which) -> proceed.run())
                    .create();
            dialog.setOnShowListener(d -> {
                int accent = UiPalette.accent(host);
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (positive != null) positive.setTextColor(accent);
                if (negative != null) negative.setTextColor(accent);
            });
            dialog.show();
        } else proceed.run();
    }

    private void importPlaylistMedia(int request, Intent data) {
        List<Uri> selected = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                selected.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) selected.add(data.getData());
        if (selected.isEmpty()) return;
        long targetList = playlist.activeId();
        confirmHundred(targetList, selected.size(), () -> addPlaylistMedia(request, selected), () -> { });
    }

    private void addPlaylistMedia(int request, List<Uri> selected) {
        int added = 0;
        int failed = 0;
        for (Uri uri : selected) {
            try {
                String mime = host.getContentResolver().getType(uri);
                boolean image = request == PICK_PLAYLIST_IMAGES;
                if (mime != null && !mime.startsWith(image ? "image/" : "video/")) {
                    failed++;
                    continue;
                }
                String name = displayName(uri);
                if (image) {
                    host.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    playlist.addLinkedImage(uri, name);
                }
                else {
                    host.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    playlist.addVideo(uri, name);
                }
                added++;
            } catch (Exception error) {
                if (!playlist.usesUri(uri) && !uri.toString().equals(
                        prefs.getString(ClockSettings.BACKGROUND_URI, ""))) {
                    try {
                        host.getContentResolver().releasePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) { }
                }
                failed++;
            }
        }
        if (added > 0) {
            prefs.edit().putString(ClockSettings.BACKGROUND_SOURCE, "playlist").apply();
            updateSingleBackgroundControls();
            if (backgroundSourceSpinner != null) backgroundSourceSpinner.setSelection(1);
            playlistChanged();
        }
        if (failed > 0) toast(t("部分文件导入失败：", "Files not imported: ") + failed);
        else if (added > 0) toast(t("已添加 ", "Added ") + added + t(" 项", " items"));
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
        if (result != Activity.RESULT_OK || data == null) return;
        if (request == PICK_PLAYLIST_FOLDER) {
            if (data.getData() != null) importPlaylistFolder(data.getData());
            return;
        }
        if (request == PICK_PLAYLIST_IMAGES || request == PICK_PLAYLIST_VIDEOS) {
            importPlaylistMedia(request, data);
            return;
        }
        if (data.getData() == null) return;
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
