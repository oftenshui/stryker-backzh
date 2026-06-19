package com.zalexdev.stryker.appintro.install;

public final class LogLine {
    public final LogLevel level;
    public final String text;

    public LogLine(LogLevel level, String text) {
        this.level = level;
        this.text = text;
    }
}
