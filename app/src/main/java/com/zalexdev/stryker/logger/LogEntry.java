package com.zalexdev.stryker.logger;

public final class LogEntry {

    public static final int INFO = 0;
    public static final int CMD = 1;
    public static final int OUT = 2;
    public static final int ERR = 3;
    public static final int WARN = 4;
    public static final int SUCCESS = 5;

    public final long id;
    public final long ts;
    public final int level;
    public final String tool;
    public final String msg;

    public LogEntry(long id, long ts, int level, String tool, String msg) {
        this.id = id;
        this.ts = ts;
        this.level = level;
        this.tool = tool == null ? "" : tool;
        this.msg = msg == null ? "" : msg;
    }
}
