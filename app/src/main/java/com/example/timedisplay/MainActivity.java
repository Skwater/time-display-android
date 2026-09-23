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

public final class MainActivity extends Activity {
    private static final int CLOSED = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final String DRAWER_STATE = "open_drawer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            face.update(System.currentTimeMillis());
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };
    private FrameLayout root;
    private BackgroundImageView image;
    private VideoView video;
    private ClockFaceView face;
    private AnimatedImageDrawable animation;
    private SettingsPanel panels;
    private ScrollView leftPanel;
    private ScrollView rightPanel;
    private View scrim;
    private View backgroundShade;
    private LinearLayout previewControls;
    private ScaleGestureDetector scaleDetector;
    private boolean active;
    private boolean previewMode;
    private float previewScale = 1f;
    private float previewPanX;
    private float previewPanY;
    private float lastTouchX;
    private float lastTouchY;
    private int openDrawer = CLOSED;
    private float downX;
    private float downY;
    private boolean gestureConsumed;
    private boolean drawerGestureLocked;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 19, 32));
        image = new BackgroundImageView(this);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        backgroundShade = new View(this);
        backgroundShade.setBackgroundColor(Color.BLACK);
        backgroundShade.setAlpha(0f);
        root.addView(backgroundShade, new FrameLayout.LayoutParams(-1, -1));
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
        loadBackground(ClockSettings.of(this));
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
        drawerGestureLocked = false;
        handler.removeCallbacks(tick);
        if (animation != null) animation.stop();
        if (video != null) video.stopPlayback();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (previewMode) finishBackgroundPreview(false);
        else if (openDrawer != CLOSED) closeDrawer();
        else super.onBackPressed();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (previewMode) return super.dispatchTouchEvent(event);
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
        if (ClockSettings.ORIENTATION.equals(key)) applyOrientation();
        if (ClockSettings.PANEL_TRANSPARENCY.equals(key)) updatePanelTransparency();
        if (ClockSettings.BACKGROUND_DIM.equals(key)) updateBackgroundShade(ClockSettings.of(this));
        if (ClockSettings.BACKGROUND_URI.equals(key) || ClockSettings.BACKGROUND_MODE.equals(key)
                || ClockSettings.BACKGROUND_TYPE.equals(key)) loadBackground(ClockSettings.of(this));
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
            previewScale = 1f;
            previewPanX = 0f;
            previewPanY = 0f;
            applyBackgroundMatrix();
        });
        save.setOnClickListener(v -> finishBackgroundPreview(true));
        cancel.setOnClickListener(v -> finishBackgroundPreview(false));
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
        leftPanel.animate().cancel();
        rightPanel.animate().cancel();
        leftPanel.setVisibility(View.GONE);
        rightPanel.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        openDrawer = CLOSED;
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
        String mode = ClockSettings.of(this).getString(ClockSettings.BACKGROUND_MODE, "fill");
        int sourceWidth = drawable.getIntrinsicWidth();
        int sourceHeight = drawable.getIntrinsicHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            image.setTileTransform(false, 1f, 0f, 0f);
            image.setScaleType("stretch".equals(mode) ? ImageView.ScaleType.FIT_XY
                    : "fit".equals(mode) ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            return;
        }
        SharedPreferences prefs = ClockSettings.of(this);
        float zoom = clamp(previewMode ? previewScale
                : prefs.getFloat(ClockSettings.BACKGROUND_SCALE, 1f), 1f, 4f);
        float panX = previewMode ? previewPanX : prefs.getFloat(ClockSettings.BACKGROUND_PAN_X, 0f);
        float panY = previewMode ? previewPanY : prefs.getFloat(ClockSettings.BACKGROUND_PAN_Y, 0f);
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
        updateBackgroundShade(prefs);
        String mode = prefs.getString(ClockSettings.BACKGROUND_MODE, "fill");
        String value = prefs.getString(ClockSettings.BACKGROUND_URI, "");
        if (value.isEmpty()) return;
        Uri uri = Uri.parse(value);
        try {
            if ("video".equals(prefs.getString(ClockSettings.BACKGROUND_TYPE, "image"))) {
                video = new VideoView(this) {
                    @Override protected void onMeasure(int widthSpec, int heightSpec) {
                        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
                    }
                };
                VideoView current = video;
                root.addView(current, 1, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                current.setVideoURI(uri);
                current.setOnPreparedListener(player -> {
                    if (video != current || !active) return;
                    player.setLooping(true);
                    player.setVolume(0, 0);
                    root.post(() -> sizeVideo(current, player, mode));
                    current.start();
                });
                current.setOnErrorListener((player, what, extra) -> {
                    backgroundShade.setAlpha(0f);
                    Toast.makeText(this, L10n.text(this, "视频无法播放，请在右侧面板更换背景", "Video cannot play; choose another background"), Toast.LENGTH_LONG).show();
                    return true;
                });
            } else {
                Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(getContentResolver(), uri));
                image.setImageDrawable(drawable);
                image.post(this::applyBackgroundMatrix);
                if (drawable instanceof AnimatedImageDrawable) {
                    animation = (AnimatedImageDrawable) drawable;
                    animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    animation.start();
                }
            }
        } catch (Exception error) {
            backgroundShade.setAlpha(0f);
            Toast.makeText(this, L10n.text(this, "背景文件无法打开，请在右侧面板重新选择", "Background cannot be opened; choose it again"), Toast.LENGTH_LONG).show();
        }
    }

    private void updateBackgroundShade(SharedPreferences prefs) {
        boolean hasBackground = !prefs.getString(ClockSettings.BACKGROUND_URI, "").isEmpty();
        int dim = Math.max(0, Math.min(70, prefs.getInt(ClockSettings.BACKGROUND_DIM, 0)));
        backgroundShade.setAlpha(hasBackground ? dim / 100f : 0f);
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
