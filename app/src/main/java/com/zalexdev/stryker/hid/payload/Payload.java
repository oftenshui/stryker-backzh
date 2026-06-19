package com.zalexdev.stryker.hid.payload;

import androidx.annotation.NonNull;

public final class Payload {

    public enum Source { ASSET, USER }

    public final String name;
    public final String displayName;
    public final String body;
    public final Source source;

    public Payload(@NonNull String name,
                   @NonNull String displayName,
                   @NonNull String body,
                   @NonNull Source source) {
        this.name = name;
        this.displayName = displayName;
        this.body = body;
        this.source = source;
    }
}
