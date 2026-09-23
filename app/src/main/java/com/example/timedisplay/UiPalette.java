package com.example.timedisplay;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

final class UiPalette {
    private UiPalette() { }

    static int accent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_accent1_600);
        }
        return 0xFF5267C8;
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
