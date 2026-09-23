package com.example.timedisplay;

import android.content.Context;

import java.util.Locale;

final class L10n {
    private L10n() { }

    static boolean english(Context context) {
        String setting = ClockSettings.of(context).getString(ClockSettings.LANGUAGE, "system");
        if ("en".equals(setting)) return true;
        if ("zh".equals(setting)) return false;
        return !"zh".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    static String text(Context context, String chinese, String english) {
        return english(context) ? english : chinese;
    }

    static Locale locale(Context context) {
        return english(context) ? Locale.ENGLISH : Locale.CHINA;
    }
}
