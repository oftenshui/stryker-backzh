package com.zalexdev.stryker.wpair;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;

import com.zalexdev.stryker.logger.LogEntry;
import com.zalexdev.stryker.logger.LogStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public final class WpairLog {

    public static final String ACTION_UPDATED = "com.zalexdev.stryker.WPAIR_LOG_UPDATED";
    private static final String LOG_FILENAME = "wpair.log";
    private static final String DIR_NAME = "wpair-logs";

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static PrintWriter writer;
    private static File currentFile;

    private WpairLog() {}

    public static File logFile(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, LOG_FILENAME);
    }

    public static void truncate(Context ctx) {
        LOCK.lock();
        try {
            closeWriterLocked();
            File f = logFile(ctx);
            if (f.exists()) f.delete();
            broadcast(ctx);
        } finally {
            LOCK.unlock();
        }
    }

    public static void info(Context ctx, String host, String message) {
        write(ctx, host, "INFO", message);
    }

    public static void append(Context ctx, String host, String tag, String body) {
        write(ctx, host, tag, body);
    }

    private static void write(Context ctx, String host, String tag, String body) {
        LOCK.lock();
        try {
            ensureWriterLocked(ctx);
            String ts = DateFormat.format("HH:mm:ss", new Date()).toString();
            long ms = System.currentTimeMillis() % 1000;
            String h = host == null ? "-" : host;
            writer.printf("%s.%03d [%s] [%s] %s%n", ts, ms, tag, h, body);
            writer.flush();
            broadcast(ctx);
        } catch (IOException e) {
        } finally {
            LOCK.unlock();
        }
        teeToStore(ctx, host, tag, body);
    }

    private static void teeToStore(Context ctx, String host, String tag, String body) {
        try {
            LogStore store = LogStore.from(ctx);
            if (store == null || body == null) return;
            int level = tag != null && tag.toUpperCase().contains("ERR") ? LogEntry.ERR : LogEntry.INFO;
            String h = host == null || host.isEmpty() ? null : host;
            store.add(level, "wpair", h == null ? body : "[" + h + "] " + body);
        } catch (Throwable ignored) {
        }
    }

    private static void ensureWriterLocked(Context ctx) throws IOException {
        File f = logFile(ctx);
        if (writer != null && f.equals(currentFile)) return;
        closeWriterLocked();
        currentFile = f;
        writer = new PrintWriter(new FileWriter(f, true));
    }

    private static void closeWriterLocked() {
        if (writer != null) {
            try { writer.flush(); writer.close(); } catch (Exception ignored) {}
            writer = null;
        }
    }

    private static void broadcast(Context ctx) {
        try {
            Intent i = new Intent(ACTION_UPDATED);
            i.setPackage(ctx.getPackageName());
            ctx.getApplicationContext().sendBroadcast(i);
        } catch (Throwable ignored) {}
    }
}
