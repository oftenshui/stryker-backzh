package com.zalexdev.stryker.hid.keymap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Keymap {

    public final String code;
    public final String displayName;
    private final Map<Integer, KeyEntry> codepointToEntry;

    Keymap(@NonNull String code,
           @NonNull String displayName,
           @NonNull Map<Integer, KeyEntry> codepointToEntry) {
        this.code = code;
        this.displayName = displayName;
        this.codepointToEntry = Collections.unmodifiableMap(new HashMap<>(codepointToEntry));
    }

    @Nullable
    public KeyEntry lookup(int codepoint) {
        return codepointToEntry.get(codepoint);
    }

    public int size() {
        return codepointToEntry.size();
    }
}
