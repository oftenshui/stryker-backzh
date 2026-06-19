package com.zalexdev.stryker.routerscan.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.zalexdev.stryker.logger.LogEntry;
import com.zalexdev.stryker.logger.LogStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class RouterScanLog {

    public static final String ACTION_UPDATED = "com.zalexdev.stryker.rs.UPDATED";

    private static final String DIR_NAME = "rs-logs";
    private static final String FILE_NAME = "router-scan.log";

    private static PrintWriter sharedWriter;
    private static final ReentrantLock writerLock = new ReentrantLock();

    private RouterScanLog() {}

    public static File logFile(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), DIR_NAME);
        if (!dir.exists())
            dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    public static void truncate(Context ctx) {
        writerLock.lock();
        try {
            closeLocked();
            File f = logFile(ctx);
            f.delete();
        } finally {
            writerLock.unlock();
        }
        broadcast(ctx);
    }

    public static void append(Context ctx, String host, String stream, String line) {
        if (line == null) return;
        writerLock.lock();
        try {
            PrintWriter pw = openLocked(ctx);
            if (pw == null) return;
            pw.println(stamp() + " [" + stream + "] [" + safeHost(host) + "] " + line);
            pw.flush();
        } finally {
            writerLock.unlock();
        }
        broadcast(ctx);
        teeToStore(ctx, host, stream, line);
    }

    private static void teeToStore(Context ctx, String host, String stream, String line) {
        try {
            LogStore store = LogStore.from(ctx);
            if (store == null) return;
            int level;
            if ("ERR".equalsIgnoreCase(stream)) level = LogEntry.ERR;
            else if ("OUT".equalsIgnoreCase(stream)) level = LogEntry.OUT;
            else level = LogEntry.INFO;
            String h = safeHost(host);
            store.add(level, "routerscan", "rs".equals(h) ? line : "[" + h + "] " + line);
        } catch (Throwable ignored) {
        }
    }

    public static void info(Context ctx, String host, String message) {
        append(ctx, host, "INFO", message);
    }

    private static PrintWriter openLocked(Context ctx) {
        if (sharedWriter != null) return sharedWriter;
        try {
            sharedWriter = new PrintWriter(new FileWriter(logFile(ctx), true));
            return sharedWriter;
        } catch (IOException e) {
            Log.w("RouterScanLog", "Could not open log: " + e.getMessage());
            return null;
        }
    }

    private static void closeLocked() {
        if (sharedWriter != null) {
            try { sharedWriter.flush(); } catch (Throwable ignored) {}
            try { sharedWriter.close(); } catch (Throwable ignored) {}
            sharedWriter = null;
        }
    }

    private static void broadcast(Context ctx) {
        try {
            Intent i = new Intent(ACTION_UPDATED);
            i.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String safeHost(String host) {
        if (host == null || host.isEmpty()) return "rs";
        return host;
    }
}
