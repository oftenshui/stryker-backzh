package com.zalexdev.stryker.hid.ui.editor;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.hid.ducky.DuckyParseException;
import com.zalexdev.stryker.hid.ducky.DuckyParser;
import com.zalexdev.stryker.hid.ducky.Program;

public final class LiveSyntaxValidator implements TextWatcher {

    public interface Listener {
        void onValid(@NonNull Program program);
        void onEmpty();
        void onError(int line, @NonNull String message);
    }

    private static final long DEBOUNCE_MS = 250;

    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable validateTask = this::validateNow;
    private final DuckyParser parser = new DuckyParser();
    private String pendingSource = "";

    public LiveSyntaxValidator(@NonNull Listener listener) {
        this.listener = listener;
    }

    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

    @Override
    public void afterTextChanged(Editable s) {
        pendingSource = s == null ? "" : s.toString();
        handler.removeCallbacks(validateTask);
        handler.postDelayed(validateTask, DEBOUNCE_MS);
    }

    public void validateNow() {
        handler.removeCallbacks(validateTask);
        String src = pendingSource;
        if (src.trim().isEmpty()) {
            listener.onEmpty();
            return;
        }
        try {
            Program p = parser.parse(src);
            listener.onValid(p);
        } catch (DuckyParseException e) {
            listener.onError(e.line, e.getMessage());
        }
    }

    public void dispose() {
        handler.removeCallbacks(validateTask);
    }
}
