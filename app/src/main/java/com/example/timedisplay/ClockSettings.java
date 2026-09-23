package com.example.timedisplay;

import android.content.Context;
import android.content.SharedPreferences;

final class ClockSettings {
    static final String PREFS = "clock_settings";
    static final String ZONE = "zone";
    static final String SHOW_ZONE = "show_zone";
    static final String ORIENTATION = "orientation";
    static final String SHOW_DATE = "show_date";
    static final String SHOW_LUNAR = "show_lunar";
    static final String SHOW_SECONDS = "show_seconds";
    static final String TEXT_SHADOW = "text_shadow";
    static final String FONT_BOLD = "font_bold";
    static final String BACKGROUND_URI = "background_uri";
    static final String BACKGROUND_TYPE = "background_type";
    static final String BACKGROUND_MODE = "background_mode";
    static final String BACKGROUND_DIM = "background_dim";
    static final String BACKGROUND_SCALE = "background_scale";
    static final String BACKGROUND_PAN_X = "background_pan_x";
    static final String BACKGROUND_PAN_Y = "background_pan_y";
    static final String PANEL_TRANSPARENCY = "panel_transparency";
    static final String FONT = "font";
    static final String FONT_FILE = "font_file";

    private ClockSettings() { }

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
