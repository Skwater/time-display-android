package com.example.timedisplay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.VideoView;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int CLOSED = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final String DRAWER_STATE = "open_drawer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            face.update(System.currentTimeMillis());
            long now = System.currentTimeMillis();
            boolean seconds = ClockSettings.of(MainActivity.this)
                    .getBoolean(ClockSettings.SHOW_SECONDS, true);
            handler.postDelayed(this, seconds ? 1000 - now % 1000 : 60000 - now % 60000);
        }
    };
    private FrameLayout root;
    private View defaultBackgroundLayer;
    private BackgroundImageView image;
    private VideoView video;
    private ClockFaceView face;
    private AnimatedImageDrawable animation;
    private SettingsPanel panels;
    private ScrollView leftPanel;
    private ScrollView rightPanel;
    private View scrim;
    private View backgroundShade;
    private View fadeCover;
    private LinearLayout previewControls;
    private ScaleGestureDetector scaleDetector;
    private boolean active;
    private boolean previewMode;
    private boolean textPreviewMode;
    private float previewScale = 1f;
    private float previewPanX;
    private float previewPanY;
    private float previewTextPanX;
    private float previewTextPanY;
    private float lastTouchX;
    private float lastTouchY;
    private int openDrawer = CLOSED;
    private float downX;
    private float downY;
    private boolean gestureConsumed;
    private boolean drawerGestureLocked;
    private boolean playlistPlayback;
    private List<PlaylistStore.Entry> playlistItems = new ArrayList<>();
    private final List<Integer> playlistOrder = new ArrayList<>();
    private int playlistPosition;
    private int mediaGeneration;
    private int playlistFailures;
    private boolean playlistPaused;
    private boolean playlistCurrentIsImage;
    private boolean playlistImageReady;
    private long playlistImageDeadlineMs;
    private long playlistImageRemainingMs;
    private long playlistImageDurationMs;
    private MediaPlayer currentVideoPlayer;
    private String requestedPlaylistId;
    private final Runnable playlistAdvance = this::advancePlaylist;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        defaultBackgroundLayer = new View(this);
        root.addView(defaultBackgroundLayer, new FrameLayout.LayoutParams(-1, -1));
        updateDefaultBackgroundColor(ClockSettings.of(this));
        image = new BackgroundImageView(this);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        backgroundShade = new View(this);
        backgroundShade.setBackgroundColor(Color.BLACK);
        backgroundShade.setAlpha(0f);
        root.addView(backgroundShade, new FrameLayout.LayoutParams(-1, -1));
        fadeCover = new View(this);
        fadeCover.setBackgroundColor(Color.BLACK);
        fadeCover.setAlpha(0f);
        root.addView(fadeCover, new FrameLayout.LayoutParams(-1, -1));
        face = new ClockFaceView(this);
        root.addView(face, new FrameLayout.LayoutParams(-1, -1));

        scrim = new View(this);
        scrim.setBackgroundColor(Color.TRANSPARENT);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panels = new SettingsPanel(this);
        leftPanel = panels.createSettingsPanel();
        rightPanel = panels.createCustomizationPanel();
        leftPanel.setVisibility(View.GONE);
        rightPanel.setVisibility(View.GONE);
        root.addView(leftPanel, new FrameLayout.LayoutParams(1, -1, Gravity.START));
        root.addView(rightPanel, new FrameLayout.LayoutParams(1, -1, Gravity.END));
        createPreviewControls();
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        if (image.getWidth() <= 0 || image.getHeight() <= 0) return false;
                        float oldScale = previewScale;
                        float nextScale = clamp(oldScale * detector.getScaleFactor(), 1f, 4f);
                        float factor = nextScale / oldScale;
                        float cx = image.getWidth() / 2f;
                        float cy = image.getHeight() / 2f;
                        previewPanX = (previewPanX * image.getWidth() * factor
                                + (detector.getFocusX() - cx) * (1f - factor)) / image.getWidth();
                        previewPanY = (previewPanY * image.getHeight() * factor
                                + (detector.getFocusY() - cy) * (1f - factor)) / image.getHeight();
                        previewScale = nextScale;
                        applyBackgroundMatrix();
                        return true;
                    }
                });
        setContentView(root);
        root.post(this::applyImmersiveMode);
        PlaylistStore.scheduleStartupCleanup(getApplicationContext());

        root.post(() -> {
            int width = Math.min(dp(340), Math.round(root.getWidth() * 0.82f));
            setPanelWidth(leftPanel, width, Gravity.START);
            setPanelWidth(rightPanel, width, Gravity.END);
            if (state != null) showDrawer(state.getInt(DRAWER_STATE, CLOSED), false);
        });
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(DRAWER_STATE, openDrawer);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        active = true;
        panels.refreshAccent();
        int accent = UiPalette.accent(this);
        for (int i = 0; i < previewControls.getChildCount(); i++) {
            View child = previewControls.getChildAt(i);
            if (child.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) child.getBackground()).setColor(UiPalette.withAlpha(accent, 210));
            }
        }
        applyOrientation();
        updateDefaultBackgroundColor(ClockSettings.of(this));
        loadBackground(ClockSettings.of(this));
        panels.refreshPlaylistPauseButton();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        }
    }

    @Override protected void onPause() {
        active = false;
        mediaGeneration++;
        handler.removeCallbacks(playlistAdvance);
        fadeCover.animate().cancel();
        drawerGestureLocked = false;
        handler.removeCallbacks(tick);
        if (animation != null) animation.stop();
        if (video != null) video.stopPlayback();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (textPreviewMode) finishTextPositionPreview(false);
        else if (previewMode) finishBackgroundPreview(false);
        else if (openDrawer == RIGHT && panels.onBackPressed()) return;
        else if (openDrawer != CLOSED) closeDrawer();
        else super.onBackPressed();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (previewMode || textPreviewMode) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            gestureConsumed = false;
        } else if (drawerGestureLocked) {
            return super.dispatchTouchEvent(event);
        } else if (action == MotionEvent.ACTION_MOVE && !gestureConsumed) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > dp(64) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                if (openDrawer == CLOSED) {
                    showDrawer(dx > 0 ? LEFT : RIGHT, true);
                    gestureConsumed = true;
                } else if ((openDrawer == LEFT && dx < 0) || (openDrawer == RIGHT && dx > 0)) {
                    closeDrawer();
                    gestureConsumed = true;
                }
                if (gestureConsumed) return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (gestureConsumed) {
                gestureConsumed = false;
                return true;
            }
        }
        return gestureConsumed || super.dispatchTouchEvent(event);
    }

    void setDrawerGestureLocked(boolean locked) {
        drawerGestureLocked = locked;
    }

    void onSettingChanged(String key) {
        if (ClockSettings.LANGUAGE.equals(key)) {
            recreate();
            return;
        }
        face.update(System.currentTimeMillis());
        if (ClockSettings.SHOW_SECONDS.equals(key)) {
            handler.removeCallbacks(tick);
            tick.run();
        }
        if (ClockSettings.ORIENTATION.equals(key)) applyOrientation();
        if (ClockSettings.PANEL_TRANSPARENCY.equals(key)) updatePanelTransparency();
        if (ClockSettings.DEFAULT_BACKGROUND_OPACITY.equals(key)
                || ClockSettings.DEFAULT_BACKGROUND_INTENSITY.equals(key)
                || ClockSettings.DEFAULT_BACKGROUND_HUE.equals(key)
                || ClockSettings.DEFAULT_BACKGROUND_SATURATION.equals(key))
            updateDefaultBackgroundColor(ClockSettings.of(this));
        if (ClockSettings.BACKGROUND_DIM.equals(key) || ClockSettings.PLAYLIST_DIM.equals(key))
            updateBackgroundShade(ClockSettings.of(this));
        boolean playlistSetting = ClockSettings.PLAYLIST_ITEMS.equals(key)
                || ClockSettings.PLAYLIST_IMAGE_MODE.equals(key)
                || ClockSettings.PLAYLIST_VIDEO_MODE.equals(key)
                || ClockSettings.PLAYLIST_FADE.equals(key)
                || ClockSettings.PLAYLIST_SHUFFLE.equals(key)
                || ClockSettings.PLAYLIST_LOOP.equals(key)
                || ClockSettings.PLAYLIST_INTERVAL.equals(key);
        if (playlistSetting && !playlistPlayback && !"playlist".equals(ClockSettings.of(this)
                .getString(ClockSettings.BACKGROUND_SOURCE, "single"))) return;
        if (ClockSettings.PLAYLIST_IMAGE_MODE.equals(key)) {
            if (playlistCurrentIsImage) applyBackgroundMatrix();
            return;
        }
        if (ClockSettings.PLAYLIST_VIDEO_MODE.equals(key)) {
            if (video != null && currentVideoPlayer != null) sizeVideo(video, currentVideoPlayer,
                    ClockSettings.of(this).getString(ClockSettings.PLAYLIST_VIDEO_MODE, "fill"));
            return;
        }
        if (ClockSettings.PLAYLIST_FADE.equals(key)) return;
        if (ClockSettings.PLAYLIST_LOOP.equals(key)) {
            if (currentVideoPlayer != null && playlistItems.size() == 1)
                currentVideoPlayer.setLooping(ClockSettings.of(this)
                        .getBoolean(ClockSettings.PLAYLIST_LOOP, true));
            return;
        }
        if (ClockSettings.PLAYLIST_INTERVAL.equals(key)) {
            updatePlaylistImageInterval();
            return;
        }
        if (ClockSettings.PLAYLIST_SHUFFLE.equals(key)) {
            updateRemainingPlaylistOrder(ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_SHUFFLE, false));
            return;
        }
        if (ClockSettings.PLAYLIST_ITEMS.equals(key) && playlistPlayback && !playlistOrder.isEmpty()) {
            requestedPlaylistId = playlistItems.get(playlistOrder.get(playlistPosition)).id;
        }
        if (ClockSettings.BACKGROUND_URI.equals(key) || ClockSettings.BACKGROUND_MODE.equals(key)
                || ClockSettings.BACKGROUND_TYPE.equals(key)
                || ClockSettings.BACKGROUND_SOURCE.equals(key)
                || ClockSettings.PLAYLIST_ITEMS.equals(key)
                || ClockSettings.PLAYLIST_IMAGE_MODE.equals(key)
                || ClockSettings.PLAYLIST_VIDEO_MODE.equals(key)
                || ClockSettings.PLAYLIST_FADE.equals(key)
                || ClockSettings.PLAYLIST_SHUFFLE.equals(key)
                || ClockSettings.PLAYLIST_LOOP.equals(key)
                || ClockSettings.PLAYLIST_INTERVAL.equals(key)) loadBackground(ClockSettings.of(this));
        if ((ClockSettings.BACKGROUND_SOURCE.equals(key) || ClockSettings.PLAYLIST_ITEMS.equals(key))
                && panels != null)
            panels.refreshPlaylistPauseButton();
    }

    boolean isPlaylistPaused() { return playlistPaused; }
    boolean isPlaylistActive() { return playlistPlayback; }

    void togglePlaylistPause() {
        if (!playlistPlayback) return;
        playlistPaused = !playlistPaused;
        if (playlistPaused) {
            fadeCover.animate().cancel();
            fadeCover.setAlpha(0f);
            if (playlistCurrentIsImage) {
                if (playlistImageReady) playlistImageRemainingMs = Math.max(1,
                        playlistImageDeadlineMs - SystemClock.uptimeMillis());
                handler.removeCallbacks(playlistAdvance);
                if (animation != null) animation.stop();
            }
            if (video != null && video.isPlaying()) video.pause();
        } else {
            if (playlistCurrentIsImage) {
                if (animation != null) animation.start();
                schedulePlaylistImageAdvance();
            }
            if (video != null) video.start();
        }
    }

    private void updateRemainingPlaylistOrder(boolean shuffle) {
        if (!playlistPlayback || playlistOrder.size() < 2) return;
        List<Integer> remaining = new ArrayList<>(playlistOrder.subList(playlistPosition + 1,
                playlistOrder.size()));
        if (shuffle) Collections.shuffle(remaining);
        else Collections.sort(remaining);
        for (int i = 0; i < remaining.size(); i++)
            playlistOrder.set(playlistPosition + 1 + i, remaining.get(i));
    }

    private void updatePlaylistImageInterval() {
        if (!playlistPlayback || !playlistCurrentIsImage || !playlistImageReady) return;
        long remaining = playlistPaused ? playlistImageRemainingMs
                : Math.max(0, playlistImageDeadlineMs - SystemClock.uptimeMillis());
        long elapsed = Math.max(0, playlistImageDurationMs - remaining);
        playlistImageDurationMs = Math.max(3, Math.min(60, ClockSettings.of(this)
                .getInt(ClockSettings.PLAYLIST_INTERVAL, 10))) * 1000L;
        playlistImageRemainingMs = Math.max(1, playlistImageDurationMs - elapsed);
        if (!playlistPaused) schedulePlaylistImageAdvance();
    }

    void playPlaylistEntry(String id) {
        requestedPlaylistId = id;
        playlistPaused = false;
        SharedPreferences prefs = ClockSettings.of(this);
        prefs.edit().putString(ClockSettings.BACKGROUND_SOURCE, "playlist").apply();
        loadBackground(prefs);
        panels.refreshPlaylistPauseButton();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        panels.onActivityResult(request, result, data);
    }

    private void applyOrientation() {
        String value = ClockSettings.of(this).getString(ClockSettings.ORIENTATION, "auto");
        int requested = "portrait".equals(value) ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : "landscape".equals(value) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (getRequestedOrientation() != requested) setRequestedOrientation(requested);
    }

    private void setPanelWidth(ScrollView panel, int width, int gravity) {
        panel.setLayoutParams(new FrameLayout.LayoutParams(width, -1, gravity));
    }

    private void showDrawer(int side, boolean animate) {
        if (side != LEFT && side != RIGHT) return;
        ScrollView panel = side == LEFT ? leftPanel : rightPanel;
        ScrollView opposite = side == LEFT ? rightPanel : leftPanel;
        opposite.animate().cancel();
        opposite.setVisibility(View.GONE);
        scrim.setVisibility(View.VISIBLE);
        openDrawer = side;
        panel.animate().cancel();
        panel.setVisibility(View.VISIBLE);
        float outside = side == LEFT ? -panel.getLayoutParams().width : panel.getLayoutParams().width;
        panel.setTranslationX(animate ? outside : 0);
        if (animate) panel.animate().translationX(0).setDuration(220).start();
    }

    private void closeDrawer() {
        if (openDrawer == CLOSED) return;
        int closing = openDrawer;
        ScrollView panel = closing == LEFT ? leftPanel : rightPanel;
        openDrawer = CLOSED;
        panel.animate().cancel();
        float outside = closing == LEFT ? -panel.getLayoutParams().width : panel.getLayoutParams().width;
        panel.animate().translationX(outside).setDuration(220).withEndAction(() -> {
            panel.setVisibility(View.GONE);
            if (openDrawer == CLOSED) {
                scrim.setVisibility(View.GONE);
            }
        }).start();
    }

    private void updatePanelTransparency() {
        int transparency = Math.round(clamp(ClockSettings.of(this)
                .getInt(ClockSettings.PANEL_TRANSPARENCY, 5), 0, 80));
        int color = Color.argb(Math.round(255 * (100 - transparency) / 100f), 20, 29, 44);
        leftPanel.setBackgroundColor(color);
        rightPanel.setBackgroundColor(color);
    }

    private void createPreviewControls() {
        previewControls = new LinearLayout(this);
        previewControls.setOrientation(LinearLayout.HORIZONTAL);
        previewControls.setGravity(Gravity.CENTER);
        previewControls.setVisibility(View.GONE);
        Button reset = previewButton(L10n.text(this, "重置", "Reset"));
        Button save = previewButton(L10n.text(this, "保存", "Save"));
        Button cancel = previewButton(L10n.text(this, "取消", "Cancel"));
        reset.setOnClickListener(v -> {
            if (textPreviewMode) {
                previewTextPanX = 0f;
                previewTextPanY = 0f;
                face.setPreviewTextPosition(0f, 0f);
            } else {
                previewScale = 1f;
                previewPanX = 0f;
                previewPanY = 0f;
                applyBackgroundMatrix();
            }
        });
        save.setOnClickListener(v -> {
            if (textPreviewMode) finishTextPositionPreview(true);
            else finishBackgroundPreview(true);
        });
        cancel.setOnClickListener(v -> {
            if (textPreviewMode) finishTextPositionPreview(false);
            else finishBackgroundPreview(false);
        });
        previewControls.addView(reset, previewButtonParams());
        previewControls.addView(save, previewButtonParams());
        previewControls.addView(cancel, previewButtonParams());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(28);
        root.addView(previewControls, params);
    }

    private Button previewButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.withAlpha(UiPalette.accent(this), 210));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setBackgroundTintList(null);
        return button;
    }

    private LinearLayout.LayoutParams previewButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(68), dp(40));
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    void startBackgroundPreview() {
        SharedPreferences prefs = ClockSettings.of(this);
        if ("playlist".equals(prefs.getString(ClockSettings.BACKGROUND_SOURCE, "single"))) {
            Toast.makeText(this, L10n.text(this, "播放列表不支持裁切位置调整", "Playlist crop position cannot be adjusted"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (prefs.getString(ClockSettings.BACKGROUND_URI, "").isEmpty()) {
            Toast.makeText(this, L10n.text(this, "请先选择图片背景", "Choose an image first"), Toast.LENGTH_SHORT).show();
            return;
        }
        if ("video".equals(prefs.getString(ClockSettings.BACKGROUND_TYPE, "image"))) {
            Toast.makeText(this, L10n.text(this, "视频背景暂不支持位置调整", "Video position editing is unavailable"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (image.getDrawable() == null || image.getWidth() == 0) {
            Toast.makeText(this, L10n.text(this, "图片尚未加载，请稍后重试", "Image is still loading; try again"), Toast.LENGTH_SHORT).show();
            return;
        }
        hideDrawersForPreview();
        previewScale = prefs.getFloat(ClockSettings.BACKGROUND_SCALE, 1f);
        previewPanX = prefs.getFloat(ClockSettings.BACKGROUND_PAN_X, 0f);
        previewPanY = prefs.getFloat(ClockSettings.BACKGROUND_PAN_Y, 0f);
        previewMode = true;
        face.setVisibility(View.GONE);
        previewControls.setVisibility(View.VISIBLE);
        image.setOnTouchListener((view, event) -> onPreviewTouch(event));
        image.setClickable(true);
        applyBackgroundMatrix();
    }

    void startTextPositionPreview() {
        if (previewMode || textPreviewMode) return;
        hideDrawersForPreview();
        SharedPreferences prefs = ClockSettings.of(this);
        previewTextPanX = prefs.getFloat(ClockSettings.TEXT_PAN_X, 0f);
        previewTextPanY = prefs.getFloat(ClockSettings.TEXT_PAN_Y, 0f);
        textPreviewMode = true;
        face.setTextPositionPreview(true, previewTextPanX, previewTextPanY);
        previewControls.setVisibility(View.VISIBLE);
        face.setOnTouchListener((view, event) -> onTextPreviewTouch(event));
        face.setClickable(true);
    }

    private void hideDrawersForPreview() {
        leftPanel.animate().cancel();
        rightPanel.animate().cancel();
        leftPanel.setVisibility(View.GONE);
        rightPanel.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        openDrawer = CLOSED;
    }

    private void finishTextPositionPreview(boolean save) {
        if (!textPreviewMode) return;
        if (save) ClockSettings.of(this).edit()
                .putFloat(ClockSettings.TEXT_PAN_X, face.previewTextPanX())
                .putFloat(ClockSettings.TEXT_PAN_Y, face.previewTextPanY()).apply();
        textPreviewMode = false;
        face.setOnTouchListener(null);
        face.setClickable(false);
        face.setTextPositionPreview(false, 0f, 0f);
        previewControls.setVisibility(View.GONE);
        showDrawer(RIGHT, false);
    }

    private boolean onTextPreviewTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && face.getWidth() > 0 && face.getHeight() > 0) {
                    previewTextPanX += (event.getX() - lastTouchX) / face.getWidth();
                    previewTextPanY += (event.getY() - lastTouchY) / face.getHeight();
                    face.setPreviewTextPosition(previewTextPanX, previewTextPanY);
                    previewTextPanX = face.previewTextPanX();
                    previewTextPanY = face.previewTextPanY();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                lastTouchX = event.getX(remaining);
                lastTouchY = event.getY(remaining);
                break;
            default:
                break;
        }
        return true;
    }

    private void finishBackgroundPreview(boolean save) {
        if (!previewMode) return;
        SharedPreferences prefs = ClockSettings.of(this);
        if (save) {
            prefs.edit().putFloat(ClockSettings.BACKGROUND_SCALE, previewScale)
                    .putFloat(ClockSettings.BACKGROUND_PAN_X, previewPanX)
                    .putFloat(ClockSettings.BACKGROUND_PAN_Y, previewPanY).apply();
        }
        previewMode = false;
        image.setOnTouchListener(null);
        image.setClickable(false);
        previewControls.setVisibility(View.GONE);
        face.setVisibility(View.VISIBLE);
        applyBackgroundMatrix();
        showDrawer(RIGHT, false);
    }

    private boolean onPreviewTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    previewPanX += (event.getX() - lastTouchX) / image.getWidth();
                    previewPanY += (event.getY() - lastTouchY) / image.getHeight();
                    applyBackgroundMatrix();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                lastTouchX = event.getX(remaining);
                lastTouchY = event.getY(remaining);
                break;
            default:
                break;
        }
        return true;
    }

    private void applyBackgroundMatrix() {
        Drawable drawable = image.getDrawable();
        int width = image.getWidth();
        int height = image.getHeight();
        if (drawable == null || width <= 0 || height <= 0) return;
        SharedPreferences prefs = ClockSettings.of(this);
        String mode = playlistPlayback ? prefs.getString(ClockSettings.PLAYLIST_IMAGE_MODE, "fill")
                : prefs.getString(ClockSettings.BACKGROUND_MODE, "fill");
        int sourceWidth = drawable.getIntrinsicWidth();
        int sourceHeight = drawable.getIntrinsicHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            image.setTileTransform(false, 1f, 0f, 0f);
            image.setScaleType("stretch".equals(mode) ? ImageView.ScaleType.FIT_XY
                    : "fit".equals(mode) ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            return;
        }
        float zoom = playlistPlayback ? 1f : clamp(previewMode ? previewScale
                : prefs.getFloat(ClockSettings.BACKGROUND_SCALE, 1f), 1f, 4f);
        float panX = playlistPlayback ? 0f : previewMode ? previewPanX
                : prefs.getFloat(ClockSettings.BACKGROUND_PAN_X, 0f);
        float panY = playlistPlayback ? 0f : previewMode ? previewPanY
                : prefs.getFloat(ClockSettings.BACKGROUND_PAN_Y, 0f);
        if ("tile".equals(mode)) {
            image.setTileTransform(true, zoom, panX * width, panY * height);
            return;
        }
        image.setTileTransform(false, 1f, 0f, 0f);
        float scaleX = width / (float) sourceWidth;
        float scaleY = height / (float) sourceHeight;
        if ("fill".equals(mode)) scaleX = scaleY = Math.max(scaleX, scaleY);
        if ("fit".equals(mode)) scaleX = scaleY = Math.min(scaleX, scaleY);
        scaleX *= zoom;
        scaleY *= zoom;
        float displayedWidth = sourceWidth * scaleX;
        float displayedHeight = sourceHeight * scaleY;
        float offsetX = clamp(panX * width, -Math.max(0f, (displayedWidth - width) / 2f),
                Math.max(0f, (displayedWidth - width) / 2f));
        float offsetY = clamp(panY * height, -Math.max(0f, (displayedHeight - height) / 2f),
                Math.max(0f, (displayedHeight - height) / 2f));
        if (previewMode) {
            previewPanX = offsetX / width;
            previewPanY = offsetY / height;
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate((width - displayedWidth) / 2f + offsetX,
                (height - displayedHeight) / 2f + offsetY);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setImageMatrix(matrix);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void loadBackground(SharedPreferences prefs) {
        handler.removeCallbacks(playlistAdvance);
        fadeCover.animate().cancel();
        fadeCover.setAlpha(0f);
        playlistPlayback = "playlist".equals(prefs.getString(ClockSettings.BACKGROUND_SOURCE, "single"));
        if (!playlistPlayback) playlistPaused = false;
        playlistItems = playlistPlayback ? new PlaylistStore(this).entries() : new ArrayList<>();
        playlistOrder.clear();
        playlistPosition = 0;
        playlistFailures = 0;
        updateBackgroundShade(prefs);
        if (playlistPlayback) {
            for (int i = 0; i < playlistItems.size(); i++) playlistOrder.add(i);
            if (prefs.getBoolean(ClockSettings.PLAYLIST_SHUFFLE, false)) Collections.shuffle(playlistOrder);
            if (requestedPlaylistId != null) {
                for (int i = 0; i < playlistOrder.size(); i++) {
                    if (playlistItems.get(playlistOrder.get(i)).id.equals(requestedPlaylistId)) {
                        playlistPosition = i;
                        break;
                    }
                }
                requestedPlaylistId = null;
            }
            if (playlistOrder.isEmpty()) {
                clearCurrentMedia();
                return;
            }
            showPlaylistItem();
            return;
        }
        String value = prefs.getString(ClockSettings.BACKGROUND_URI, "");
        if (value.isEmpty()) {
            clearCurrentMedia();
            return;
        }
        showMedia(Uri.parse(value), prefs.getString(ClockSettings.BACKGROUND_TYPE, "image"),
                prefs.getString(ClockSettings.BACKGROUND_MODE, "fill"));
    }

    private void sampleStaticBackground(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source source) {
        if (info.isAnimated()) return;
        int maxEdge = Math.max(root.getWidth(), root.getHeight());
        if (maxEdge <= 0) {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            maxEdge = Math.max(metrics.widthPixels, metrics.heightPixels);
        }
        int width = info.getSize().getWidth();
        int height = info.getSize().getHeight();
        int sample = 1;
        while (sample < 8 && (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge)) {
            sample *= 2;
        }
        if (sample > 1) decoder.setTargetSampleSize(sample);
    }

    private void clearCurrentMedia() {
        mediaGeneration++;
        handler.removeCallbacks(playlistAdvance);
        playlistCurrentIsImage = false;
        playlistImageReady = false;
        playlistImageRemainingMs = 0;
        playlistImageDurationMs = 0;
        playlistImageDeadlineMs = 0;
        currentVideoPlayer = null;
        if (video != null) {
            video.stopPlayback();
            root.removeView(video);
            video = null;
        }
        if (animation != null) {
            animation.stop();
            animation = null;
        }
        image.setImageDrawable(null);
        image.setTileTransform(false, 1f, 0f, 0f);
    }

    private void showPlaylistItem() {
        if (playlistOrder.isEmpty()) return;
        PlaylistStore.Entry entry = playlistItems.get(playlistOrder.get(playlistPosition));
        SharedPreferences prefs = ClockSettings.of(this);
        String mode = "video".equals(entry.type)
                ? prefs.getString(ClockSettings.PLAYLIST_VIDEO_MODE, "fill")
                : prefs.getString(ClockSettings.PLAYLIST_IMAGE_MODE, "fill");
        showMedia(Uri.parse(entry.uri), entry.type, mode);
    }

    private void showMedia(Uri uri, String type, String mode) {
        clearCurrentMedia();
        int generation = mediaGeneration;
        try {
            if ("video".equals(type)) {
                video = new VideoView(this) {
                    @Override protected void onMeasure(int widthSpec, int heightSpec) {
                        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
                    }
                };
                VideoView current = video;
                root.addView(current, 1, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                current.setVideoURI(uri);
                current.setOnPreparedListener(player -> {
                    if (video != current || !active || mediaGeneration != generation) return;
                    currentVideoPlayer = player;
                    player.setLooping(!playlistPlayback || (playlistItems.size() == 1
                            && ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_LOOP, true)));
                    player.setVolume(0, 0);
                    root.post(() -> sizeVideo(current, player, mode));
                    if (playlistPlayback) {
                        player.setOnInfoListener((media, what, extra) -> {
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) revealMedia(generation);
                            return false;
                        });
                        current.setOnCompletionListener(media -> {
                            if (mediaGeneration == generation) advancePlaylist();
                        });
                    }
                    current.start();
                    if (playlistPlayback && playlistPaused) current.pause();
                    handler.postDelayed(() -> revealMedia(generation), 1200);
                });
                current.setOnErrorListener((player, what, extra) -> {
                    if (mediaGeneration != generation) return true;
                    if (playlistPlayback) skipFailedPlaylistItem();
                    else {
                        backgroundShade.setAlpha(0f);
                        Toast.makeText(this, L10n.text(this, "视频无法播放，请在右侧面板更换背景", "Video cannot play; choose another background"), Toast.LENGTH_LONG).show();
                    }
                    return true;
                });
            } else {
                Drawable drawable = ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(getContentResolver(), uri),
                        this::sampleStaticBackground);
                image.setImageDrawable(drawable);
                image.post(() -> {
                    if (mediaGeneration != generation) return;
                    applyBackgroundMatrix();
                    revealMedia(generation);
                    playlistImageReady = true;
                    if (playlistPlayback && playlistItems.size() > 1) {
                        int seconds = Math.max(3, Math.min(60, ClockSettings.of(this)
                                .getInt(ClockSettings.PLAYLIST_INTERVAL, 10)));
                        playlistImageDurationMs = seconds * 1000L;
                        playlistImageRemainingMs = playlistImageDurationMs;
                        if (!playlistPaused) schedulePlaylistImageAdvance();
                    }
                });
                playlistCurrentIsImage = playlistPlayback;
                if (drawable instanceof AnimatedImageDrawable) {
                    animation = (AnimatedImageDrawable) drawable;
                    animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    animation.start();
                    if (playlistPaused) animation.stop();
                }
            }
        } catch (Exception error) {
            if (playlistPlayback) skipFailedPlaylistItem();
            else {
                backgroundShade.setAlpha(0f);
                fadeCover.setAlpha(0f);
                Toast.makeText(this, L10n.text(this, "背景文件无法打开，请在右侧面板重新选择", "Background cannot be opened; choose it again"), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void revealMedia(int generation) {
        if (mediaGeneration != generation || !active) return;
        playlistFailures = 0;
        if (fadeCover.getAlpha() > 0f) {
            fadeCover.animate().cancel();
            fadeCover.animate().alpha(0f).setDuration(250).start();
        }
    }

    private void advancePlaylist() {
        if (!playlistPlayback || !active || playlistPaused || playlistItems.size() < 2) return;
        if (playlistPosition + 1 >= playlistOrder.size()
                && !ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_LOOP, true)) return;
        if (ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_FADE, true)) {
            int generation = mediaGeneration;
            fadeCover.animate().cancel();
            fadeCover.animate().alpha(1f).setDuration(250).withEndAction(() -> {
                if (mediaGeneration == generation && active) showNextPlaylistItem();
            }).start();
        } else showNextPlaylistItem();
    }

    private void schedulePlaylistImageAdvance() {
        handler.removeCallbacks(playlistAdvance);
        if (!playlistPlayback || playlistPaused || !playlistCurrentIsImage || !playlistImageReady
                || playlistItems.size() < 2)
            return;
        playlistImageDeadlineMs = SystemClock.uptimeMillis() + Math.max(1, playlistImageRemainingMs);
        handler.postAtTime(playlistAdvance, playlistImageDeadlineMs);
    }

    private void showNextPlaylistItem() {
        playlistPosition++;
        if (playlistPosition >= playlistOrder.size()) {
            playlistPosition = 0;
            if (ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_SHUFFLE, false)) {
                Collections.shuffle(playlistOrder);
            }
        }
        showPlaylistItem();
    }

    private void skipFailedPlaylistItem() {
        playlistFailures++;
        if (playlistFailures >= playlistItems.size()) {
            clearCurrentMedia();
            fadeCover.setAlpha(0f);
            Toast.makeText(this, L10n.text(this, "播放列表中的媒体无法打开", "Playlist media could not be opened"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (playlistPosition + 1 >= playlistOrder.size()
                && !ClockSettings.of(this).getBoolean(ClockSettings.PLAYLIST_LOOP, true)) {
            clearCurrentMedia();
            fadeCover.setAlpha(0f);
            Toast.makeText(this, L10n.text(this, "播放列表末项无法打开", "The last playlist item could not be opened"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        handler.post(this::showNextPlaylistItem);
    }

    private void updateBackgroundShade(SharedPreferences prefs) {
        boolean hasBackground = "playlist".equals(prefs.getString(ClockSettings.BACKGROUND_SOURCE, "single"))
                ? !new PlaylistStore(this).entries().isEmpty()
                : !prefs.getString(ClockSettings.BACKGROUND_URI, "").isEmpty();
        int dim = Math.max(0, Math.min(70, prefs.getInt(
                "playlist".equals(prefs.getString(ClockSettings.BACKGROUND_SOURCE, "single"))
                        ? ClockSettings.PLAYLIST_DIM : ClockSettings.BACKGROUND_DIM, 0)));
        backgroundShade.setAlpha(hasBackground ? dim / 100f : 0f);
    }

    private void updateDefaultBackgroundColor(SharedPreferences prefs) {
        defaultBackgroundLayer.setBackgroundColor(ClockSettings.defaultBackgroundColor(prefs));
    }

    private void sizeVideo(VideoView current, MediaPlayer player, String mode) {
        if (video != current || root.getWidth() == 0 || root.getHeight() == 0) return;
        int width = root.getWidth();
        int height = root.getHeight();
        if (!"stretch".equals(mode) && player.getVideoWidth() > 0 && player.getVideoHeight() > 0) {
            float widthScale = width / (float) player.getVideoWidth();
            float heightScale = height / (float) player.getVideoHeight();
            float scale = "fill".equals(mode) ? Math.max(widthScale, heightScale)
                    : Math.min(widthScale, heightScale);
            width = Math.round(player.getVideoWidth() * scale);
            height = Math.round(player.getVideoHeight() * scale);
        }
        current.setLayoutParams(new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
