package com.example.timedisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class ClockSettings {
    static final String PREFS = "clock_settings";
    static final String ZONE = "zone";
    static final String SHOW_ZONE = "show_zone";
    static final String ORIENTATION = "orientation";
    static final String LANGUAGE = "language";
    static final String SHOW_DATE = "show_date";
    static final String SHOW_LUNAR = "show_lunar";
    static final String SHOW_SECONDS = "show_seconds";
    static final String TEXT_SHADOW = "text_shadow";
    static final String FONT_BOLD = "font_bold";
    static final String BACKGROUND_URI = "background_uri";
    static final String BACKGROUND_TYPE = "background_type";
    static final String BACKGROUND_MODE = "background_mode";
    static final String BACKGROUND_DIM = "background_dim";
    static final String DEFAULT_BACKGROUND_OPACITY = "default_background_opacity";
    static final String DEFAULT_BACKGROUND_INTENSITY = "default_background_intensity";
    static final String DEFAULT_BACKGROUND_HUE = "default_background_hue";
    static final String DEFAULT_BACKGROUND_SATURATION = "default_background_saturation";
    static final String BACKGROUND_SCALE = "background_scale";
    static final String BACKGROUND_PAN_X = "background_pan_x";
    static final String BACKGROUND_PAN_Y = "background_pan_y";
    static final String BACKGROUND_SOURCE = "background_source";
    static final String PLAYLIST_ITEMS = "playlist_items";
    static final String PLAYLIST_ACTIVE_ID = "playlist_active_id";
    static final String PLAYLIST_IMAGE_MODE = "playlist_image_mode";
    static final String PLAYLIST_VIDEO_MODE = "playlist_video_mode";
    static final String PLAYLIST_FADE = "playlist_fade";
    static final String PLAYLIST_SHUFFLE = "playlist_shuffle";
    static final String PLAYLIST_LOOP = "playlist_loop";
    static final String PLAYLIST_INTERVAL = "playlist_interval";
    static final String PLAYLIST_DIM = "playlist_dim";
    static final String PANEL_TRANSPARENCY = "panel_transparency";
    static final String FONT = "font";
    static final String FONT_FILE = "font_file";
    static final String FONT_LIBRARY = "font_library";
    static final String FONT_SIZE_PERCENT = "font_size_percent";
    static final String FONT_OPACITY = "font_opacity";
    static final String FONT_INTENSITY = "font_intensity";
    static final String FONT_HUE = "font_hue";
    static final String FONT_SATURATION = "font_saturation";

    private ClockSettings() { }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int fontColor(SharedPreferences prefs) {
        int alpha = Math.round(255 * bounded(prefs.getInt(FONT_OPACITY, 100), 100) / 100f);
        float hue = bounded(prefs.getInt(FONT_HUE, 0), 360);
        float saturation = bounded(prefs.getInt(FONT_SATURATION, 0), 100) / 100f;
        float intensity = bounded(prefs.getInt(FONT_INTENSITY, 100), 100) / 100f;
        return Color.HSVToColor(alpha, new float[]{hue, saturation, intensity});
    }

    static int defaultBackgroundColor(SharedPreferences prefs) {
        int alpha = Math.round(255 * bounded(prefs.getInt(DEFAULT_BACKGROUND_OPACITY, 100), 100) / 100f);
        float hue = bounded(prefs.getInt(DEFAULT_BACKGROUND_HUE, 219), 360);
        float saturation = bounded(prefs.getInt(DEFAULT_BACKGROUND_SATURATION, 63), 100) / 100f;
        float intensity = bounded(prefs.getInt(DEFAULT_BACKGROUND_INTENSITY, 13), 100) / 100f;
        return Color.HSVToColor(alpha, new float[]{hue, saturation, intensity});
    }

    private static int bounded(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }
}
