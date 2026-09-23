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
    private boolean textPositionPreview;
    private float previewPanX;
    private float previewPanY;
    private float maxPanX;
    private float maxPanY;

    ClockFaceView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void update(long millis) {
        now = millis;
        invalidate();
    }

    void setTextPositionPreview(boolean enabled, float x, float y) {
        textPositionPreview = enabled;
        previewPanX = x;
        previewPanY = y;
        invalidate();
    }

    void setPreviewTextPosition(float x, float y) {
        previewPanX = Math.max(-maxPanX, Math.min(maxPanX, x));
        previewPanY = Math.max(-maxPanY, Math.min(maxPanY, y));
        invalidate();
    }

    float previewTextPanX() { return previewPanX; }
    float previewTextPanY() { return previewPanY; }

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
        boolean english = L10n.english(getContext());
        boolean seconds = prefs.getBoolean(ClockSettings.SHOW_SECONDS, true);
        String time = dateTime.format(DateTimeFormatter.ofPattern(seconds ? "HH:mm:ss" : "HH:mm", Locale.ROOT));
        List<String> details = new ArrayList<>();
        if (prefs.getBoolean(ClockSettings.SHOW_ZONE, true)) {
            details.add("SYSTEM".equals(zoneId)
                    ? L10n.text(getContext(), "系统时区 · ", "System time zone · ") + zone.getId()
                    : zone.getId());
        }
        if (prefs.getBoolean(ClockSettings.SHOW_DATE, true)) {
            details.add(dateTime.format(DateTimeFormatter.ofPattern(
                    english ? "EEEE, MMM d, yyyy" : "yyyy年M月d日 EEEE",
                    L10n.locale(getContext()))));
        }
        if (prefs.getBoolean(ClockSettings.SHOW_LUNAR, false)) {
            details.add(lunarDate(now, zone.getId()));
        }

        paint.setTypeface(loadTypeface(prefs));
        paint.setTextAlign(Paint.Align.CENTER);
        int fontColor = ClockSettings.fontColor(prefs);
        paint.setColor(fontColor);
        if (prefs.getBoolean(ClockSettings.TEXT_SHADOW, true)) {
            paint.setShadowLayer(dp(8), 0, dp(3), Color.argb(Color.alpha(fontColor), 0, 0, 0));
        } else {
            paint.clearShadowLayer();
        }
        float maxWidth = Math.max(1, getWidth() - dp(32));
        float fontScale = Math.max(50, Math.min(200,
                prefs.getInt(ClockSettings.FONT_SIZE_PERCENT, 100))) / 100f;
        float timeSize = Math.min(getWidth() * 0.16f, getHeight() * 0.28f);
        timeSize = Math.max(dp(24), timeSize) * fontScale;
        paint.setTextSize(timeSize);
        float minimumTimeSize = dp(9);
        while (paint.measureText(time) > maxWidth && timeSize > minimumTimeSize) {
            timeSize = Math.max(minimumTimeSize, timeSize - dp(2) * fontScale);
            paint.setTextSize(timeSize);
        }
        float detailSize = Math.max(dp(14),
                Math.min(getWidth() * 0.035f, getHeight() * 0.05f)) * fontScale;
        float lineHeight = detailSize * 1.6f;
        float gap = dp(14) * fontScale;
        float blockHeight = timeSize + gap + details.size() * lineHeight;
        float availableHeight = Math.max(1, getHeight() - dp(24));
        float layoutFit = 1f;
        if (blockHeight > availableHeight) {
            layoutFit = availableHeight / blockHeight;
            timeSize *= layoutFit;
            detailSize *= layoutFit;
            lineHeight *= layoutFit;
            gap *= layoutFit;
            blockHeight = timeSize + gap + details.size() * lineHeight;
        }
        float[] detailSizes = new float[details.size()];
        paint.setTextSize(timeSize);
        float widest = paint.measureText(time);
        for (int i = 0; i < details.size(); i++) {
            String detail = details.get(i);
            paint.setTextSize(detailSize);
            float minimumDetailSize = dp(6) * layoutFit;
            while (paint.measureText(detail) > maxWidth && paint.getTextSize() > minimumDetailSize) {
                paint.setTextSize(Math.max(minimumDetailSize, paint.getTextSize() - dp(1) * fontScale));
            }
            detailSizes[i] = paint.getTextSize();
            widest = Math.max(widest, paint.measureText(detail));
        }
        maxPanX = Math.max(0f, (getWidth() - widest) / 2f - dp(16)) / Math.max(1, getWidth());
        maxPanY = Math.max(0f, (getHeight() - blockHeight) / 2f - dp(12)) / Math.max(1, getHeight());
        float savedX = textPositionPreview ? previewPanX : prefs.getFloat(ClockSettings.TEXT_PAN_X, 0f);
        float savedY = textPositionPreview ? previewPanY : prefs.getFloat(ClockSettings.TEXT_PAN_Y, 0f);
        float panX = Math.max(-maxPanX, Math.min(maxPanX, savedX));
        float panY = Math.max(-maxPanY, Math.min(maxPanY, savedY));
        if (textPositionPreview) {
            previewPanX = panX;
            previewPanY = panY;
        }
        int restore = canvas.save();
        canvas.translate(panX * getWidth(), panY * getHeight());
        float y = (getHeight() - blockHeight) / 2f + timeSize;
        paint.setTextSize(timeSize);
        canvas.drawText(time, getWidth() / 2f, y, paint);
        for (int i = 0; i < details.size(); i++) {
            y += lineHeight;
            paint.setTextSize(detailSizes[i]);
            String detail = details.get(i);
            canvas.drawText(detail, getWidth() / 2f, y, paint);
        }
        canvas.restoreToCount(restore);
        String separator = english ? ", " : "，";
        setContentDescription(time + separator + String.join(separator, details));
    }

    private String lunarDate(long millis, String zoneId) {
        ChineseCalendar calendar = new ChineseCalendar(android.icu.util.TimeZone.getTimeZone(zoneId));
        calendar.setTimeInMillis(millis);
        int month = calendar.get(ChineseCalendar.MONTH);
        int day = calendar.get(ChineseCalendar.DATE);
        if (month < 0 || month >= LUNAR_MONTHS.length || day < 1 || day > LUNAR_DAYS.length) {
            return L10n.text(getContext(), "农历日期不可用", "Lunar date unavailable");
        }
        boolean leapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
        if (L10n.english(getContext())) {
            return "Lunar " + (leapMonth ? "leap " : "") + "month " + (month + 1) + ", day " + day;
        }
        String leap = leapMonth ? "闰" : "";
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
            File imported = FontLibrary.fileFor(getContext(), style);
            if (imported != null && imported.isFile()) base = Typeface.createFromFile(imported);
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
