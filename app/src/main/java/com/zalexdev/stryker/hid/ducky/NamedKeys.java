package com.zalexdev.stryker.hid.ducky;

import com.zalexdev.stryker.hid.report.UsbHid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NamedKeys {

    private NamedKeys() {}

    public static final Map<String, Integer> MODIFIERS;
    public static final Map<String, Integer> KEYS;

    static {
        Map<String, Integer> m = new HashMap<>();
        m.put("CTRL",        UsbHid.MOD_LEFT_CTRL);
        m.put("CONTROL",     UsbHid.MOD_LEFT_CTRL);
        m.put("SHIFT",       UsbHid.MOD_LEFT_SHIFT);
        m.put("ALT",         UsbHid.MOD_LEFT_ALT);
        m.put("GUI",         UsbHid.MOD_LEFT_GUI);
        m.put("WINDOWS",     UsbHid.MOD_LEFT_GUI);
        m.put("WIN",         UsbHid.MOD_LEFT_GUI);
        m.put("META",        UsbHid.MOD_LEFT_GUI);
        m.put("COMMAND",     UsbHid.MOD_LEFT_GUI);
        m.put("SUPER",       UsbHid.MOD_LEFT_GUI);
        m.put("RIGHTCTRL",   UsbHid.MOD_RIGHT_CTRL);
        m.put("RIGHTSHIFT",  UsbHid.MOD_RIGHT_SHIFT);
        m.put("RIGHTALT",    UsbHid.MOD_RIGHT_ALT);
        m.put("RIGHTGUI",    UsbHid.MOD_RIGHT_GUI);
        m.put("ALTGR",       UsbHid.MOD_RIGHT_ALT);
        MODIFIERS = Collections.unmodifiableMap(m);

        Map<String, Integer> k = new HashMap<>();
        k.put("ENTER",       UsbHid.KEY_ENTER);
        k.put("RETURN",      UsbHid.KEY_ENTER);
        k.put("ESC",         UsbHid.KEY_ESC);
        k.put("ESCAPE",      UsbHid.KEY_ESC);
        k.put("BACKSPACE",   UsbHid.KEY_BACKSPACE);
        k.put("BKSP",        UsbHid.KEY_BACKSPACE);
        k.put("DEL",         UsbHid.KEY_DELETE);
        k.put("DELETE",      UsbHid.KEY_DELETE);
        k.put("TAB",         UsbHid.KEY_TAB);
        k.put("SPACE",       UsbHid.KEY_SPACE);
        k.put("CAPSLOCK",    UsbHid.KEY_CAPS_LOCK);
        k.put("NUMLOCK",     UsbHid.KEY_NUMLOCK);
        k.put("SCROLLLOCK",  UsbHid.KEY_SCROLLLOCK);
        k.put("INSERT",      UsbHid.KEY_INSERT);
        k.put("HOME",        UsbHid.KEY_HOME);
        k.put("END",         UsbHid.KEY_END);
        k.put("PAGEUP",      UsbHid.KEY_PAGE_UP);
        k.put("PAGEDOWN",    UsbHid.KEY_PAGE_DOWN);
        k.put("PRINTSCREEN", UsbHid.KEY_PRINTSCR);
        k.put("PRTSCN",      UsbHid.KEY_PRINTSCR);
        k.put("PRTSC",       UsbHid.KEY_PRINTSCR);
        k.put("PAUSE",       UsbHid.KEY_PAUSE);
        k.put("BREAK",       UsbHid.KEY_PAUSE);
        k.put("MENU",        UsbHid.KEY_MENU);
        k.put("APP",         UsbHid.KEY_APPLICATION);
        k.put("APPLICATION", UsbHid.KEY_APPLICATION);
        k.put("UP",          UsbHid.KEY_UP);
        k.put("DOWN",        UsbHid.KEY_DOWN);
        k.put("LEFT",        UsbHid.KEY_LEFT);
        k.put("RIGHT",       UsbHid.KEY_RIGHT);
        k.put("UPARROW",     UsbHid.KEY_UP);
        k.put("DOWNARROW",   UsbHid.KEY_DOWN);
        k.put("LEFTARROW",   UsbHid.KEY_LEFT);
        k.put("RIGHTARROW",  UsbHid.KEY_RIGHT);
        for (int i = 1; i <= 12; i++) {
            k.put("F" + i, UsbHid.KEY_F1 + (i - 1));
        }
        for (int i = 13; i <= 24; i++) {
            k.put("F" + i, UsbHid.KEY_F13 + (i - 13));
        }
        KEYS = Collections.unmodifiableMap(k);
    }

    public static Integer modifierFor(String token) {
        return MODIFIERS.get(token.toUpperCase(Locale.ROOT));
    }

    public static Integer keyFor(String token) {
        return KEYS.get(token.toUpperCase(Locale.ROOT));
    }

    public static boolean isModifier(String token) {
        return MODIFIERS.containsKey(token.toUpperCase(Locale.ROOT));
    }
}
