package com.zalexdev.stryker.geomac.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import com.zalexdev.stryker.geomac.model.GeoPin;

public final class GeoMarkers {

    public static final int COLOR_LOOKUP = Color.parseColor("#1565C0");
    public static final int COLOR_SCAN = Color.parseColor("#7E57C2");
    public static final int COLOR_CRACKED_HS = Color.parseColor("#F57C00");
    public static final int COLOR_CRACKED_PIXIE = Color.parseColor("#2E7D32");
    public static final int COLOR_MANUAL = Color.parseColor("#D81B60");

    private static final int W_DP = 32;
    private static final int H_DP = 42;

    private static final LruCache<Integer, Bitmap> CACHE = new LruCache<>(16);

    private GeoMarkers() {}

    public static int colorOf(GeoPin.Category category) {
        switch (category) {
            case SCAN: return COLOR_SCAN;
            case CRACKED_HANDSHAKE: return COLOR_CRACKED_HS;
            case CRACKED_PIXIE: return COLOR_CRACKED_PIXIE;
            case MANUAL: return COLOR_MANUAL;
            case LOOKUP:
            default: return COLOR_LOOKUP;
        }
    }

    public static String labelOf(GeoPin.Category category) {
        switch (category) {
            case SCAN: return "Scanned";
            case CRACKED_HANDSHAKE: return "Handshake";
            case CRACKED_PIXIE: return "Pixie WPS";
            case MANUAL: return "Manual";
            case LOOKUP:
            default: return "Looked up";
        }
    }

    public static Drawable forCategory(Context context, GeoPin.Category category) {
        int color = colorOf(category);
        Bitmap bmp = CACHE.get(color);
        if (bmp == null) {
            bmp = render(context, color);
            CACHE.put(color, bmp);
        }
        return new BitmapDrawable(context.getResources(), bmp);
    }

    private static Bitmap render(Context context, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.round(W_DP * density);
        int h = Math.round(H_DP * density);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x33000000);
        shadow.setStyle(Paint.Style.FILL);

        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setColor(color);
        body.setStyle(Paint.Style.FILL);

        Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        disc.setColor(Color.WHITE);
        disc.setStyle(Paint.Style.FILL);

        Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        glyph.setColor(color);

        float cx = w / 2f;
        float headR = w * 0.46f;
        float headCy = headR + 2f * density;

        Path shadowPath = teardrop(cx, headCy + 1.5f * density, headR, h - 2f * density);
        c.drawPath(shadowPath, shadow);

        Path bodyPath = teardrop(cx, headCy, headR, h - 4f * density);
        c.drawPath(bodyPath, body);

        float discR = headR * 0.62f;
        c.drawCircle(cx, headCy, discR, disc);

        drawWifiGlyph(c, cx, headCy, discR, glyph);

        return out;
    }

    private static Path teardrop(float cx, float cy, float r, float bottomY) {
        Path p = new Path();
        p.moveTo(cx - r * 0.78f, cy + r * 0.55f);
        p.lineTo(cx + r * 0.78f, cy + r * 0.55f);
        p.lineTo(cx, bottomY);
        p.close();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }

    private static void drawWifiGlyph(Canvas c, float cx, float discCy, float discR, Paint paint) {
        float originX = cx;
        float originY = discCy + discR * 0.55f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(discR * 0.22f);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float[] arcRadii = { discR * 1.05f, discR * 0.72f, discR * 0.38f };
        for (float r : arcRadii) {
            RectF box = new RectF(originX - r, originY - r, originX + r, originY + r);
            c.drawArc(box, 225f, 90f, false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(originX, originY, discR * 0.14f, paint);
    }
}
