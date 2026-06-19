package com.zalexdev.stryker.hid.backchannel;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;

import java.io.IOException;

public class ScreenViewerActivity extends AppCompatActivity {

    private AcmFrameStream stream;
    private ImageView imageView;
    private MaterialTextView waiting;
    private MaterialTextView mouseIndicator;
    private Bitmap currentBitmap;
    private boolean closing;
    private View keyboardPanel;
    private HidKeyboardController keyboard;
    private MouseGestureController mouse;
    private boolean mouseMode;
    private com.google.android.material.button.MaterialButton mouseBtn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_viewer);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat c =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        c.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());

        imageView      = findViewById(R.id.viewer_image);
        waiting        = findViewById(R.id.viewer_waiting);
        mouseIndicator = findViewById(R.id.viewer_mouse_indicator);
        keyboardPanel  = findViewById(R.id.viewer_keyboard);
        MaterialButton closeBtn = findViewById(R.id.viewer_close_btn);
        MaterialButton kbdBtn   = findViewById(R.id.viewer_kbd_btn);
        mouseBtn = findViewById(R.id.viewer_mouse_btn);
        closeBtn.setOnClickListener(v -> finish());

        if (keyboardPanel instanceof ViewGroup) {
            keyboard = new HidKeyboardController((ViewGroup) keyboardPanel);
        }
        kbdBtn.setOnClickListener(v -> toggleKeyboard());

        mouse = new MouseGestureController();
        mouseBtn.setOnClickListener(v -> toggleMouseMode());

        imageView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (mouse != null) mouse.setImageSize(r - l, b - t);
        });

        imageView.setOnTouchListener((v, ev) -> {
            if (mouseMode && mouse != null && mouse.isOpen()) {
                return mouse.onTouch(v, ev);
            }
            return false;
        });

        long stored = new com.zalexdev.stryker.utils.Core(this).getInt("acm_reap_settle_ms");
        if (stored > 0) AcmFrameStream.setReapSettleMs(stored);

        try {
            stream = AcmFrameStream.open(new AcmFrameStream.Listener() {
                @Override public void onFrame(@NonNull byte[] jpeg, long ts, int hostW, int hostH) {
                    if (closing || isFinishing()) return;
                    Bitmap bmp;
                    try {
                        bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    } catch (OutOfMemoryError e) {
                        return;
                    }
                    if (bmp == null) return;
                    runOnUiThread(() -> {
                        if (closing || isFinishing()) {
                            bmp.recycle();
                            return;
                        }
                        Bitmap old = currentBitmap;
                        currentBitmap = bmp;
                        imageView.setImageBitmap(bmp);
                        if (old != null && !old.isRecycled()) old.recycle();
                        if (waiting.getVisibility() == View.VISIBLE) {
                            waiting.setVisibility(View.GONE);
                        }
                        if (mouse != null) mouse.setHostDimensions(hostW, hostH);
                    });
                }
                @Override public void onClosed(@Nullable Throwable cause) {
                    if (closing || isFinishing()) return;
                    runOnUiThread(() -> {
                        if (closing || isFinishing()) return;
                        finish();
                    });
                }
            });
        } catch (IOException e) {
            waiting.setText(getString(R.string.screen_mirror_no_node));
        }
    }

    private void toggleKeyboard() {
        if (keyboardPanel == null || keyboard == null) return;
        if (keyboardPanel.getVisibility() == View.VISIBLE) {
            keyboardPanel.setVisibility(View.GONE);
            keyboard.close();
        } else {
            if (!keyboard.open()) {
                android.widget.Toast.makeText(this,
                        getString(R.string.viewer_keyboard_failed),
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            View root = findViewById(R.id.viewer_root);
            if (root != null && root.getHeight() > 0) {
                android.widget.FrameLayout.LayoutParams lp =
                        (android.widget.FrameLayout.LayoutParams) keyboardPanel.getLayoutParams();
                lp.height = root.getHeight() / 2;
                keyboardPanel.setLayoutParams(lp);
            }
            keyboardPanel.setVisibility(View.VISIBLE);
        }
    }

    private void toggleMouseMode() {
        if (mouse == null) return;
        if (mouseMode) {
            mouseMode = false;
            mouse.close();
            if (mouseIndicator != null) mouseIndicator.setVisibility(View.GONE);
            if (mouseBtn != null) mouseBtn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xA0000000));
        } else {
            if (!mouse.open()) {
                String reason = mouse.getLastOpenError();
                String text = getString(R.string.viewer_mouse_failed)
                        + (reason != null ? "\n" + reason : "");
                android.widget.Toast.makeText(this, text,
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            mouse.setImageSize(imageView.getWidth(), imageView.getHeight());
            mouseMode = true;
            if (mouseIndicator != null) mouseIndicator.setVisibility(View.VISIBLE);
            if (mouseBtn != null) mouseBtn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1565C0));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closing = true;
        if (stream != null) {
            try { stream.close(); } catch (Exception ignored) {}
            stream = null;
        }
        if (keyboard != null) {
            keyboard.close();
            keyboard = null;
        }
        if (mouse != null) {
            mouse.close();
            mouse = null;
        }
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
}
