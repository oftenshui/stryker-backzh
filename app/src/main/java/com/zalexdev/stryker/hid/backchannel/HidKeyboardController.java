package com.zalexdev.stryker.hid.backchannel;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.report.HidReportStream;
import com.zalexdev.stryker.hid.report.KeyReport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class HidKeyboardController {

    private final ViewGroup root;
    private final List<View> modButtons = new ArrayList<>();
    @Nullable private HidReportStream stream;
    private int armedModifiers;

    public HidKeyboardController(@NonNull ViewGroup root) {
        this.root = root;
        wire(root);
    }

    public boolean open() {
        if (stream != null) return true;
        try {
            stream = HidReportStream.openKeyboard();
            return true;
        } catch (IOException e) {
            stream = null;
            return false;
        }
    }

    public void close() {
        if (stream != null) {
            try { stream.close(); } catch (Exception ignored) {}
            stream = null;
        }
        armedModifiers = 0;
        for (View mb : modButtons) mb.setActivated(false);
    }

    public boolean isOpen() {
        return stream != null;
    }

    private void wire(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                wire(g.getChildAt(i));
            }
            return;
        }
        Object tag = v.getTag();
        if (!(tag instanceof String)) return;
        String s = (String) tag;
        if (s.startsWith("hk:")) {
            final int keycode = parseHex(s.substring(3));
            v.setOnClickListener(b -> sendKey(keycode));
        } else if (s.startsWith("mod:")) {
            final int mask = parseHex(s.substring(4));
            modButtons.add(v);
            v.setOnClickListener(b -> toggleModifier(b, mask));
        }
    }

    private void toggleModifier(@NonNull View button, int mask) {
        if ((armedModifiers & mask) != 0) {
            armedModifiers &= ~mask;
            button.setActivated(false);
        } else {
            armedModifiers |= mask;
            button.setActivated(true);
        }
    }

    private void sendKey(int keycode) {
        if (stream == null) return;
        try {
            stream.sendKey(KeyReport.singleKey(armedModifiers, keycode));
            stream.releaseAllKeys();
        } catch (IOException ignored) {
        }
        if (armedModifiers != 0) {
            armedModifiers = 0;
            for (View mb : modButtons) mb.setActivated(false);
        }
    }

    private static int parseHex(String hex) {
        String h = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        try { return Integer.parseInt(h, 16); }
        catch (NumberFormatException e) { return 0; }
    }
}
