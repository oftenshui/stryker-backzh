package com.zalexdev.stryker.hid.ui.editor;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.hid.ducky.NamedKeys;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class DuckySyntaxHighlighter implements TextWatcher {

    private static final int COLOR_COMMENT  = 0xFF6B7280;
    private static final int COLOR_COMMAND  = 0xFF1565C0;
    private static final int COLOR_MODIFIER = 0xFF8E24AA;
    private static final int COLOR_NAMEKEY  = 0xFF00897B;
    private static final int COLOR_NUMBER   = 0xFFEF6C00;
    private static final int COLOR_STRING   = 0xFF2E7D32;

    private static final Set<String> COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "REM", "DELAY", "DEFAULTDELAY", "DEFAULT_DELAY",
            "DEFAULTCHARDELAY", "DEFAULT_CHAR_DELAY",
            "STRING", "STRINGLN", "STRINGDELAY", "STRING_DELAY",
            "REPEAT", "END_REPEAT",
            "DEFINE", "VAR", "ATTACKMODE",
            "HOLD", "RELEASE", "RELEASE_ALL",
            "IF", "ELSE", "END_IF", "WHILE", "END_WHILE",
            "FUNCTION", "END_FUNCTION", "RETURN",
            "MOUSE_MOVE", "MOUSE_CLICK", "MOUSE_SCROLL",
            "IMPORT", "INCLUDE",
            "GETWINHI", "CAPTURE", "WINHI", "SCREEN", "VIEW",
            "STARTSCREEN", "STARTVIEW"
    )));

    private boolean updating;

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable e) {
        if (updating) return;
        updating = true;
        try {
            apply(e);
        } finally {
            updating = false;
        }
    }

    public static void apply(@NonNull Editable e) {
        CharacterStyle[] old = e.getSpans(0, e.length(), CharacterStyle.class);
        for (CharacterStyle s : old) {
            if (s instanceof ForegroundColorSpan || s instanceof StyleSpan) {
                e.removeSpan(s);
            }
        }
        int lineStart = 0;
        int len = e.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || e.charAt(i) == '\n') {
                paintLine(e, lineStart, i);
                lineStart = i + 1;
            }
        }
    }

    private static void paintLine(Editable e, int from, int to) {
        if (from >= to) return;
        int contentStart = from;
        while (contentStart < to && Character.isWhitespace(e.charAt(contentStart))) {
            contentStart++;
        }
        if (contentStart >= to) return;

        int firstTokenEnd = contentStart;
        while (firstTokenEnd < to && !Character.isWhitespace(e.charAt(firstTokenEnd))) {
            firstTokenEnd++;
        }
        String firstToken = e.subSequence(contentStart, firstTokenEnd)
                .toString().toUpperCase(Locale.ROOT);

        if ("REM".equals(firstToken) || firstToken.startsWith("//")) {
            paint(e, from, to, new ForegroundColorSpan(COLOR_COMMENT));
            paint(e, from, to, new StyleSpan(Typeface.ITALIC));
            return;
        }

        if (COMMANDS.contains(firstToken)) {
            paint(e, contentStart, firstTokenEnd, new ForegroundColorSpan(COLOR_COMMAND));
            paint(e, contentStart, firstTokenEnd, new StyleSpan(Typeface.BOLD));
            int bodyStart = firstTokenEnd;
            while (bodyStart < to && Character.isWhitespace(e.charAt(bodyStart))) bodyStart++;
            if (bodyStart < to) {
                if ("STRING".equals(firstToken) || "STRINGLN".equals(firstToken)) {
                    paint(e, bodyStart, to, new ForegroundColorSpan(COLOR_STRING));
                } else if ("STRINGDELAY".equals(firstToken) || "STRING_DELAY".equals(firstToken)) {
                    int sep = bodyStart;
                    while (sep < to && !Character.isWhitespace(e.charAt(sep))) sep++;
                    paint(e, bodyStart, sep, new ForegroundColorSpan(COLOR_NUMBER));
                    while (sep < to && Character.isWhitespace(e.charAt(sep))) sep++;
                    if (sep < to) paint(e, sep, to, new ForegroundColorSpan(COLOR_STRING));
                } else if ("REM".equals(firstToken)) {
                    paint(e, bodyStart, to, new ForegroundColorSpan(COLOR_COMMENT));
                } else {
                    paintNumbers(e, bodyStart, to);
                }
            }
            return;
        }

        paintCombo(e, contentStart, to);
    }

    private static void paintCombo(Editable e, int from, int to) {
        int cursor = from;
        while (cursor < to) {
            while (cursor < to && Character.isWhitespace(e.charAt(cursor))) cursor++;
            int start = cursor;
            while (cursor < to && !Character.isWhitespace(e.charAt(cursor))) cursor++;
            if (start == cursor) break;
            String tok = e.subSequence(start, cursor).toString();
            String upper = tok.toUpperCase(Locale.ROOT);
            int color;
            if (NamedKeys.isModifier(upper)) {
                color = COLOR_MODIFIER;
            } else if (NamedKeys.keyFor(upper) != null) {
                color = COLOR_NAMEKEY;
            } else if (isInteger(tok)) {
                color = COLOR_NUMBER;
            } else {
                continue;
            }
            paint(e, start, cursor, new ForegroundColorSpan(color));
        }
    }

    private static void paintNumbers(Editable e, int from, int to) {
        int cursor = from;
        while (cursor < to) {
            while (cursor < to && !Character.isDigit(e.charAt(cursor))) cursor++;
            int start = cursor;
            while (cursor < to && Character.isDigit(e.charAt(cursor))) cursor++;
            if (start < cursor) {
                paint(e, start, cursor, new ForegroundColorSpan(COLOR_NUMBER));
            }
        }
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty()) return false;
        int i = s.charAt(0) == '-' ? 1 : 0;
        if (i == s.length()) return false;
        for (; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static void paint(Spannable s, int start, int end, Object span) {
        s.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
