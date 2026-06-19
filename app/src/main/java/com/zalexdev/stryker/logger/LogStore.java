package com.zalexdev.stryker.logger;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.utils.Core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogStore {

    private static final String TAG = "LogStore";
    public static final String ACTION_UPDATED = "com.zalexdev.stryker.LOG_UPDATED";

    private static final int MAX_ROWS = 200_000;
    private static final int TRIM_EVERY = 4_000;
    private static final int QUEUE_CAP = 20_000;
    private static final int BATCH_MAX = 512;
    private static final long MIN_BROADCAST_MS = 250L;

    private static final String DB_NAME = "stryker_logs.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "log";

    private static final Pattern MAC = Pattern.compile("((\\w{2}:){5}\\w{2})");

    private static volatile LogStore INSTANCE;

    private final Context app;
    private final DbHelper helper;
    private final BlockingQueue<Pending> queue = new ArrayBlockingQueue<>(QUEUE_CAP);
    private final Thread writerThread;
    private final ScheduledExecutorService broadcaster =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "log-broadcast");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean broadcastScheduled = new AtomicBoolean(false);
    private final AtomicLong droppedSinceLastWarn = new AtomicLong();
    private volatile long lastBroadcast = 0;
    private volatile boolean running = true;
    private int sinceTrim = 0;

    private LogStore(Context ctx) {
        this.app = ctx.getApplicationContext();
        this.helper = new DbHelper(app);
        this.writerThread = new Thread(this::writerLoop, "log-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    public static LogStore init(Context ctx) {
        LogStore local = INSTANCE;
        if (local == null) {
            synchronized (LogStore.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new LogStore(ctx);
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    @Nullable
    public static LogStore peek() {
        return INSTANCE;
    }

    public static LogStore from(Context ctx) {
        LogStore local = INSTANCE;
        return local != null ? local : init(ctx);
    }

    private static final class Pending {
        final long ts;
        final int level;
        final String tool;
        final String msg;
        final java.util.concurrent.CountDownLatch clearLatch;
        Pending(long ts, int level, String tool, String msg) {
            this.ts = ts; this.level = level; this.tool = tool; this.msg = msg;
            this.clearLatch = null;
        }
        Pending(java.util.concurrent.CountDownLatch clearLatch) {
            this.ts = 0; this.level = 0; this.tool = null; this.msg = null;
            this.clearLatch = clearLatch;
        }
    }

    public void add(int level, @Nullable String tool, @Nullable String msg) {
        if (msg == null) return;
        String safe = mask(msg);
        Pending p = new Pending(System.currentTimeMillis(), level,
                tool == null ? "" : tool, safe);
        if (!queue.offer(p)) {
            long n = droppedSinceLastWarn.incrementAndGet();
            if (n == 1 || n % 1000 == 0) {
                Log.w(TAG, "log queue full, dropped " + n + " line(s)");
            }
        }
    }

    private static String mask(String line) {
        Matcher m = MAC.matcher(line);
        if (m.find()) {
            return line.replaceAll("((\\w{2}:){5}\\w{2})", Matcher.quoteReplacement(Core.HIDDEN_MAC));
        }
        return line;
    }

    private void writerLoop() {
        List<Pending> batch = new ArrayList<>(BATCH_MAX);
        while (running) {
            try {
                Pending first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, BATCH_MAX - 1);
                processBatch(batch);
                batch.clear();
                notifyChanged();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                Log.e(TAG, "writer flush failed", t);
                batch.clear();
            }
        }
    }

    private void processBatch(List<Pending> batch) {
        if (batch.isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        List<Pending> inserts = new ArrayList<>(batch.size());
        int inserted = 0;
        for (Pending p : batch) {
            if (p.clearLatch != null) {
                inserted += insertRows(db, inserts);
                inserts.clear();
                deleteAll(db);
                p.clearLatch.countDown();
            } else {
                inserts.add(p);
            }
        }
        inserted += insertRows(db, inserts);
        sinceTrim += inserted;
        if (sinceTrim >= TRIM_EVERY) {
            sinceTrim = 0;
            trim(db);
        }
    }

    private int insertRows(SQLiteDatabase db, List<Pending> rows) {
        if (rows.isEmpty()) return 0;
        SQLiteStatement ins = db.compileStatement(
                "INSERT INTO " + TABLE + "(ts,level,tool,msg) VALUES(?,?,?,?)");
        db.beginTransaction();
        try {
            for (Pending p : rows) {
                ins.clearBindings();
                ins.bindLong(1, p.ts);
                ins.bindLong(2, p.level);
                if (p.tool == null || p.tool.isEmpty()) ins.bindNull(3);
                else ins.bindString(3, p.tool);
                ins.bindString(4, p.msg);
                ins.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            ins.close();
        }
        return rows.size();
    }

    private void deleteAll(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + TABLE);
        try { db.execSQL("VACUUM"); } catch (Throwable ignored) {}
    }

    private void trim(SQLiteDatabase db) {
        try {
            long max = DatabaseUtils.longForQuery(db, "SELECT MAX(_id) FROM " + TABLE, null);
            if (max > MAX_ROWS) {
                db.execSQL("DELETE FROM " + TABLE + " WHERE _id < ?",
                        new Object[]{max - MAX_ROWS});
            }
        } catch (Throwable t) {
            Log.w(TAG, "trim failed", t);
        }
    }

    private void notifyChanged() {
        long now = SystemClock.uptimeMillis();
        long since = now - lastBroadcast;
        if (since >= MIN_BROADCAST_MS) {
            lastBroadcast = now;
            sendBroadcastNow();
        } else if (broadcastScheduled.compareAndSet(false, true)) {
            broadcaster.schedule(() -> {
                broadcastScheduled.set(false);
                lastBroadcast = SystemClock.uptimeMillis();
                sendBroadcastNow();
            }, MIN_BROADCAST_MS - since, TimeUnit.MILLISECONDS);
        }
    }

    private void sendBroadcastNow() {
        try {
            Intent i = new Intent(ACTION_UPDATED);
            i.setPackage(app.getPackageName());
            app.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    public List<LogEntry> query(@NonNull LogFilter filter, long beforeId, int limit) {
        List<String> args = new ArrayList<>();
        String where = filter.buildWhere(beforeId, 0, args);
        String sql = "SELECT _id,ts,level,tool,msg FROM " + TABLE
                + " WHERE " + where + " ORDER BY _id DESC LIMIT " + Math.max(1, limit);
        return read(sql, args.toArray(new String[0]), false);
    }

    @NonNull
    public List<LogEntry> queryNewer(@NonNull LogFilter filter, long afterId, int limit) {
        List<String> args = new ArrayList<>();
        String where = filter.buildWhere(Long.MAX_VALUE, afterId, args);
        String sql = "SELECT _id,ts,level,tool,msg FROM " + TABLE
                + " WHERE " + where + " ORDER BY _id ASC LIMIT " + Math.max(1, limit);
        return read(sql, args.toArray(new String[0]), true);
    }

    private List<LogEntry> read(String sql, String[] args, boolean reverse) {
        List<LogEntry> out = new ArrayList<>();
        SQLiteDatabase db;
        try {
            db = helper.getReadableDatabase();
        } catch (Throwable t) {
            return out;
        }
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                out.add(new LogEntry(
                        c.getLong(0), c.getLong(1), c.getInt(2),
                        c.isNull(3) ? "" : c.getString(3),
                        c.getString(4)));
            }
        } catch (Throwable t) {
            Log.w(TAG, "query failed", t);
        }
        if (reverse) Collections.reverse(out);
        return out;
    }

    @NonNull
    public List<String> distinctTools() {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db;
        try {
            db = helper.getReadableDatabase();
        } catch (Throwable t) {
            return out;
        }
        try (Cursor c = db.rawQuery(
                "SELECT DISTINCT tool FROM " + TABLE
                        + " WHERE tool IS NOT NULL AND tool <> '' ORDER BY tool COLLATE NOCASE", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        } catch (Throwable t) {
            Log.w(TAG, "distinctTools failed", t);
        }
        return out;
    }

    public long count(@NonNull LogFilter filter) {
        List<String> args = new ArrayList<>();
        String where = filter.buildWhere(Long.MAX_VALUE, 0, args);
        SQLiteDatabase db;
        try {
            db = helper.getReadableDatabase();
        } catch (Throwable t) {
            return 0;
        }
        try {
            return DatabaseUtils.longForQuery(db,
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE " + where,
                    args.toArray(new String[0]));
        } catch (Throwable t) {
            return 0;
        }
    }

    public void clear() {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        try {
            queue.put(new Pending(latch));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        notifyChanged();
    }

    public long export(@NonNull File out, @NonNull LogFilter filter) {
        List<String> args = new ArrayList<>();
        String where = filter.buildWhere(Long.MAX_VALUE, 0, args);
        SQLiteDatabase db;
        try {
            db = helper.getReadableDatabase();
        } catch (Throwable t) {
            return -1;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        long n = 0;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out, false));
             Cursor c = db.rawQuery(
                     "SELECT ts,level,tool,msg FROM " + TABLE
                             + " WHERE " + where + " ORDER BY _id ASC",
                     args.toArray(new String[0]))) {
            w.write("==== Stryker log export ====\n");
            while (c.moveToNext()) {
                String tool = c.isNull(2) ? "" : c.getString(2);
                w.write(fmt.format(new Date(c.getLong(0))));
                w.write(" [");
                w.write(levelTag(c.getInt(1)));
                w.write("]");
                if (!tool.isEmpty()) { w.write(" ["); w.write(tool); w.write("]"); }
                w.write(" ");
                w.write(c.getString(3));
                w.write("\n");
                n++;
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "export failed", e);
            return -1;
        }
        return n;
    }

    public static String levelTag(int level) {
        switch (level) {
            case LogEntry.CMD: return "CMD";
            case LogEntry.OUT: return "OUT";
            case LogEntry.ERR: return "ERR";
            case LogEntry.WARN: return "WARN";
            case LogEntry.SUCCESS: return "OK";
            default: return "INFO";
        }
    }

    private static final class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) {
            super(ctx, DB_NAME, null, DB_VERSION);
            setWriteAheadLoggingEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ts INTEGER NOT NULL,"
                    + "level INTEGER NOT NULL,"
                    + "tool TEXT,"
                    + "msg TEXT NOT NULL)");
            db.execSQL("CREATE INDEX idx_log_level ON " + TABLE + "(level)");
            db.execSQL("CREATE INDEX idx_log_tool ON " + TABLE + "(tool)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
