package com.zalexdev.stryker.hid.report;

import androidx.annotation.NonNull;

public final class MouseReport {

    public static final int LENGTH = 4;
    public static final int BTN_LEFT   = 0x01;
    public static final int BTN_RIGHT  = 0x02;
    public static final int BTN_MIDDLE = 0x04;

    private final byte[] bytes = new byte[LENGTH];

    public MouseReport(int buttons, int dx, int dy, int wheel) {
        bytes[0] = (byte) (buttons & 0xFF);
        bytes[1] = (byte) clampSigned8(dx);
        bytes[2] = (byte) clampSigned8(dy);
        bytes[3] = (byte) clampSigned8(wheel);
    }

    @NonNull
    public byte[] toBytes() {
        byte[] copy = new byte[LENGTH];
        System.arraycopy(bytes, 0, copy, 0, LENGTH);
        return copy;
    }

    private static int clampSigned8(int v) {
        if (v > 127) return 127;
        if (v < -127) return -127;
        return v;
    }
}
