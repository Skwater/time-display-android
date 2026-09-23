package com.example.timedisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.util.ChineseCalendar;
import android.view.View;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ClockFaceView extends View {
    private static final String[] LUNAR_MONTHS = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
    private static final String[] LUNAR_DAYS = {"初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long now = System.currentTimeMillis();

    ClockFaceView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void update(long millis) {
        now = millis;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        SharedPreferences prefs = ClockSettings.of(getContext());
        String zoneId = prefs.getString(ClockSettings.ZONE, "SYSTEM");
        ZoneId zone;
        try {
            zone = "SYSTEM".equals(zoneId) ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        } catch (Exception ignored) {
            zone = ZoneId.systemDefault();
        }
        ZonedDateTime dateTime = Instant.ofEpochMilli(now).atZone(zone);
        boolean seconds = prefs.getBoolean(ClockSettings.SHOW_SECONDS, true);
        String time = dateTime.format(DateTimeFormatter.ofPattern(seconds ? "HH:mm:ss" : "HH:mm", Locale.ROOT));
        List<String> details = new ArrayList<>();
        if (prefs.getBoolean(ClockSettings.SHOW_ZONE, true)) {
            details.add("SYSTEM".equals(zoneId) ? "系统时区 · " + zone.getId() : zone.getId());
        }
        if (prefs.getBoolean(ClockSettings.SHOW_DATE, true)) {
            details.add(dateTime.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)));
        }
        if (prefs.getBoolean(ClockSettings.SHOW_LUNAR, false)) {
            details.add(lunarDate(now, zone.getId()));
        }

        paint.setTypeface(loadTypeface(prefs));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        if (prefs.getBoolean(ClockSettings.TEXT_SHADOW, true)) {
            paint.setShadowLayer(dp(8), 0, dp(3), Color.BLACK);
        } else {
            paint.clearShadowLayer();
        }
        float maxWidth = Math.max(1, getWidth() - dp(32));
        float timeSize = Math.min(getWidth() * 0.16f, getHeight() * 0.28f);
        timeSize = Math.max(dp(24), timeSize);
        paint.setTextSize(timeSize);
        while (paint.measureText(time) > maxWidth && timeSize > dp(18)) {
            timeSize -= dp(2);
            paint.setTextSize(timeSize);
        }
        float detailSize = Math.max(dp(14), Math.min(getWidth() * 0.035f, getHeight() * 0.05f));
        float lineHeight = detailSize * 1.6f;
        float blockHeight = timeSize + dp(14) + details.size() * lineHeight;
        float y = (getHeight() - blockHeight) / 2f + timeSize;
        canvas.drawText(time, getWidth() / 2f, y, paint);
        paint.setTextSize(detailSize);
        for (String detail : details) {
            y += lineHeight;
            while (paint.measureText(detail) > maxWidth && paint.getTextSize() > dp(12)) {
                paint.setTextSize(paint.getTextSize() - dp(1));
            }
            canvas.drawText(detail, getWidth() / 2f, y, paint);
            paint.setTextSize(detailSize);
        }
        setContentDescription(time + "，" + String.join("，", details));
    }

    private String lunarDate(long millis, String zoneId) {
        ChineseCalendar calendar = new ChineseCalendar(android.icu.util.TimeZone.getTimeZone(zoneId));
        calendar.setTimeInMillis(millis);
        int month = calendar.get(ChineseCalendar.MONTH);
        int day = calendar.get(ChineseCalendar.DATE);
        if (month < 0 || month >= LUNAR_MONTHS.length || day < 1 || day > LUNAR_DAYS.length) {
            return "农历日期不可用";
        }
        String leap = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1 ? "闰" : "";
        return "农历 " + leap + LUNAR_MONTHS[month] + "月" + LUNAR_DAYS[day - 1];
    }

    private Typeface loadTypeface(SharedPreferences prefs) {
        String style = prefs.getString(ClockSettings.FONT, "system");
        boolean bold = prefs.getBoolean(ClockSettings.FONT_BOLD, false);
        Typeface base = Typeface.DEFAULT;
        try {
            if ("sans".equals(style)) base = Typeface.create("sans-serif-light", Typeface.NORMAL);
            if ("serif".equals(style)) base = Typeface.SERIF;
            if ("mono".equals(style)) base = Typeface.MONOSPACE;
            if ("custom".equals(style)) {
                String path = prefs.getString(ClockSettings.FONT_FILE, "");
                if (!path.isEmpty()) base = Typeface.createFromFile(new File(path));
            }
            if (base == null) base = Typeface.DEFAULT;
            Typeface selected = Typeface.create(base, bold ? Typeface.BOLD : Typeface.NORMAL);
            return selected == null ? (bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT) : selected;
        } catch (RuntimeException ignored) {
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
