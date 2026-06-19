package com.zalexdev.stryker.hid.report;

import androidx.annotation.NonNull;

import java.util.Arrays;

public final class KeyReport {

    public static final int LENGTH = 8;
    public static final KeyReport EMPTY = new KeyReport(0, new int[0]);

    private final byte[] bytes = new byte[LENGTH];

    public KeyReport(int modifier, @NonNull int[] keycodes) {
        bytes[0] = (byte) (modifier & 0xFF);
        int slot = 2;
        for (int code : keycodes) {
            if (slot >= LENGTH) break;
            if (code <= 0) continue;
            bytes[slot++] = (byte) (code & 0xFF);
        }
    }

    public static KeyReport singleKey(int modifier, int keycode) {
        return new KeyReport(modifier, new int[]{keycode});
    }

    @NonNull
    public byte[] toBytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }
}
