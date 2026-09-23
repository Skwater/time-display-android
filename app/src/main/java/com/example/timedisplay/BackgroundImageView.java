package com.example.timedisplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

final class BackgroundImageView extends ImageView {
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private boolean tiled;
    private float tileScale = 1f;
    private float tileX;
    private float tileY;

    BackgroundImageView(Context context) {
        super(context);
    }

    void setTileTransform(boolean enabled, float scale, float x, float y) {
        tiled = enabled;
        tileScale = scale;
        tileX = x;
        tileY = y;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();
        if (!tiled || drawable == null || drawable.getIntrinsicWidth() <= 0
                || drawable.getIntrinsicHeight() <= 0) {
            super.onDraw(canvas);
            return;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                BitmapShader shader = new BitmapShader(bitmap,
                        Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                Matrix matrix = new Matrix();
                matrix.setScale(tileScale * drawable.getIntrinsicWidth() / bitmap.getWidth(),
                        tileScale * drawable.getIntrinsicHeight() / bitmap.getHeight());
                matrix.postTranslate(tileX, tileY);
                shader.setLocalMatrix(matrix);
                tilePaint.setShader(shader);
                canvas.drawRect(0, 0, getWidth(), getHeight(), tilePaint);
                tilePaint.setShader(null);
                return;
            }
        }
        float width = drawable.getIntrinsicWidth() * tileScale;
        float height = drawable.getIntrinsicHeight() * tileScale;
        if (width <= 0 || height <= 0) return;
        float estimated = (getWidth() / width + 2f) * (getHeight() / height + 2f);
        float scale = estimated > 1500f ? tileScale * (float) Math.sqrt(estimated / 1500f) : tileScale;
        width = drawable.getIntrinsicWidth() * scale;
        height = drawable.getIntrinsicHeight() * scale;
        float startX = tileX % width;
        float startY = tileY % height;
        if (startX > 0) startX -= width;
        if (startY > 0) startY -= height;
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        int drawn = 0;
        for (float y = startY; y < getHeight() && drawn < 1600; y += height) {
            for (float x = startX; x < getWidth() && drawn < 1600; x += width) {
                int save = canvas.save();
                canvas.translate(x, y);
                canvas.scale(scale, scale);
                drawable.draw(canvas);
                canvas.restoreToCount(save);
                drawn++;
            }
        }
    }
}
