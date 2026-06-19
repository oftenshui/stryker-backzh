package com.zalexdev.stryker.hid.configfs;

public enum GadgetFunction {
    HID_KEYBOARD("hid_keyboard", "hid.keyboard"),
    HID_MOUSE("hid_mouse", "hid.mouse"),
    MASS_STORAGE("mass_storage", "mass_storage.0"),
    RNDIS("rndis", "rndis.usb0"),
    ECM("ecm", "ecm.usb0"),
    ACM("acm", "acm.0"),
    ADB("adb", "ffs.adb");

    public final String key;
    public final String functionDir;

    GadgetFunction(String key, String functionDir) {
        this.key = key;
        this.functionDir = functionDir;
    }
}
