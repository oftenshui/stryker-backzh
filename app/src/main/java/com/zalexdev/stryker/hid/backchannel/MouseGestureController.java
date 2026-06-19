package com.zalexdev.stryker.hid.backchannel;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.report.HidReportStream;
import com.zalexdev.stryker.hid.report.MouseReport;

import java.io.IOException;

public final class MouseGestureController {

    private static final int  CLICK_SLOP_PX     = 12;
    private static final long LONG_PRESS_MS     = 500L;
    private static final long CLICK_MAX_MS      = 250L;
    private static final float SCROLL_DETENT_PX = 24f;

    @Nullable private HidReportStream mouse;
    @Nullable private String lastOpenError;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float scaleX = 1f, scaleY = 1f;
    private int hostW, hostH;
    private int imageW, imageH;

    private float anchorX, anchorY;
    private float lastX,   lastY;
    private long  downTime;
    private int   activePointers;
    private boolean moved;
    private boolean rightClickFired;
    private boolean scrollMode;
    private float   scrollAccumulator;

    public boolean open() {
        if (mouse != null) return true;
        try {
            mouse = HidReportStream.openMouse();
            lastOpenError = null;
            return true;
        } catch (IOException e) {
            mouse = null;
            lastOpenError = e.getMessage();
            return false;
        }
    }

    @Nullable
    public String getLastOpenError() {
        return lastOpenError;
    }

    public void close() {
        if (mouse != null) {
            try { mouse.close(); } catch (Exception ignored) {}
            mouse = null;
        }
        handler.removeCallbacksAndMessages(null);
        reset();
    }

    public boolean isOpen() {
        return mouse != null;
    }

    public void setHostDimensions(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == this.hostW && h == this.hostH) return;
        this.hostW = w;
        this.hostH = h;
        recomputeScale();
    }

    public void setImageSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == this.imageW && h == this.imageH) return;
        this.imageW = w;
        this.imageH = h;
        recomputeScale();
    }

    private void recomputeScale() {
        if (imageW > 0 && imageH > 0 && hostW > 0 && hostH > 0) {
            scaleX = (float) hostW / (float) imageW;
            scaleY = (float) hostH / (float) imageH;
        } else {
            scaleX = scaleY = 1f;
        }
    }

    public boolean onTouch(@NonNull View v, @NonNull MotionEvent ev) {
        if (mouse == null) return false;
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                anchorX = lastX = ev.getX();
                anchorY = lastY = ev.getY();
                downTime = System.currentTimeMillis();
                activePointers = 1;
                moved = false;
                rightClickFired = false;
                scrollMode = false;
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                activePointers++;
                if (activePointers == 2) {
                    handler.removeCallbacks(longPressRunnable);
                    scrollMode = true;
                    scrollAccumulator = 0f;
                    lastY = midY(ev);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (scrollMode) {
                    float midY = midY(ev);
                    float deltaY = lastY - midY;
                    lastY = midY;
                    scrollAccumulator += deltaY;
                    while (Math.abs(scrollAccumulator) >= SCROLL_DETENT_PX) {
                        int dir = scrollAccumulator > 0 ? 1 : -1;
                        sendMouse(0, 0, 0, dir);
                        scrollAccumulator -= dir * SCROLL_DETENT_PX;
                    }
                } else {
                    float curX = ev.getX();
                    float curY = ev.getY();
                    float dxF = (curX - lastX) * scaleX;
                    float dyF = (curY - lastY) * scaleY;
                    lastX = curX;
                    lastY = curY;
                    sendScaledMove(dxF, dyF);
                    if (Math.hypot(curX - anchorX, curY - anchorY) > CLICK_SLOP_PX) {
                        moved = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                activePointers--;
                if (activePointers < 2) {
                    scrollMode = false;
                    scrollAccumulator = 0f;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                long elapsed = System.currentTimeMillis() - downTime;
                if (!moved && !rightClickFired && !scrollMode
                        && elapsed < CLICK_MAX_MS && action == MotionEvent.ACTION_UP) {
                    int button = activePointers >= 1
                            ? MouseReport.BTN_MIDDLE
                            : MouseReport.BTN_LEFT;
                    click(button);
                }
                reset();
                return true;
        }
        return false;
    }

    private final Runnable longPressRunnable = () -> {
        if (moved || scrollMode) return;
        rightClickFired = true;
        click(MouseReport.BTN_RIGHT);
    };

    private static float midY(MotionEvent ev) {
        float sum = 0;
        int n = ev.getPointerCount();
        for (int i = 0; i < n; i++) sum += ev.getY(i);
        return sum / n;
    }

    private void reset() {
        activePointers = 0;
        moved = false;
        scrollMode = false;
        scrollAccumulator = 0f;
    }

    private void sendScaledMove(float dxF, float dyF) {
        int dx = Math.round(dxF);
        int dy = Math.round(dyF);
        while (dx != 0 || dy != 0) {
            int sx = Math.max(-127, Math.min(127, dx));
            int sy = Math.max(-127, Math.min(127, dy));
            sendMouse(0, sx, sy, 0);
            dx -= sx;
            dy -= sy;
        }
    }

    private void click(int button) {
        sendMouse(button, 0, 0, 0);
        sendMouse(0, 0, 0, 0);
    }

    private void sendMouse(int buttons, int dx, int dy, int wheel) {
        HidReportStream m = mouse;
        if (m == null) return;
        try {
            m.sendMouse(new MouseReport(buttons, dx, dy, wheel));
        } catch (IOException ignored) {
        }
    }
}
