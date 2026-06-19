package com.zalexdev.stryker.nuclei;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.NucleiItem;
import com.zalexdev.stryker.custom.Site;
import com.zalexdev.stryker.utils.Core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NucleiScanService extends Service {

    public static final String EXTRA_SITE_ID = "id";
    public static final String EXTRA_SEVERITY = "severity";
    public static final String ACTION_CANCEL = "com.zalexdev.stryker.nuclei.CANCEL";
    public static final String ACTION_UPDATED = "com.zalexdev.stryker.nuclei.UPDATED";

    private static final String CHANNEL_ID = "nuclei_scan_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 4090;
    private static final int RESULT_NOTIFICATION_BASE = 4091;

    private Core core;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private ExecutorService executor;

    private final Map<Integer, Process> running = new ConcurrentHashMap<>();
    private final Set<Integer> queued = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> severities = new ConcurrentHashMap<>();
    private final Map<Integer, PrintWriter> logWriters = new ConcurrentHashMap<>();

    public static File terminalLog(android.content.Context ctx, int siteId) {
        File dir = new File(ctx.getExternalFilesDir(null), "nuclei-logs");
        if (!dir.exists())
            dir.mkdirs();
        return new File(dir, "nuclei-" + siteId + ".log");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        core = new Core(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nuclei-scan-worker");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stryker:nuclei");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }

        if (ACTION_CANCEL.equals(intent.getAction())) {
            int siteId = intent.getIntExtra(EXTRA_SITE_ID, -1);
            cancelSite(siteId);
            return START_NOT_STICKY;
        }

        int siteId = intent.getIntExtra(EXTRA_SITE_ID, -1);
        if (siteId < 0) {
            stopIfIdle();
            return START_NOT_STICKY;
        }

        String severity = intent.getStringExtra(EXTRA_SEVERITY);
        if (severity != null) severities.put(siteId, severity);

        startInForeground();
        if (!wakeLock.isHeld()) wakeLock.acquire(6L * 60 * 60 * 1000);

        queued.add(siteId);
        executor.submit(() -> runScan(siteId));
        return START_NOT_STICKY;
    }

    private void runScan(int siteId) {
        queued.remove(siteId);
        Site site = loadSite(siteId);
        if (site == null) {
            Log.w("NucleiScanService", "Site " + siteId + " gone from prefs — skipping");
            stopIfIdle();
            return;
        }

        openLog(siteId, site.getUrl());

        site.status = "Running";
        site.progress = "0";
        persist(site, siteId);
        updateForegroundNotification(site);
        broadcast(siteId);

        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            running.put(siteId, process);
            site.pid = String.valueOf(getPidOfProcess(process));
            persist(site, siteId);

            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();

            stdin.write((Core.EXECUTE + "'ash'\n").getBytes());
            for (String line : buildNucleiScript(site.getUrl(), severities.get(siteId))) {
                stdin.write((line + "\n").getBytes());
            }
            stdin.write("exit\nexit\n".getBytes());
            stdin.flush();
            stdin.close();

            Thread stderrReader = new Thread(() -> readStderr(stderr, siteId), "nuclei-stderr-" + siteId);
            stderrReader.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(stdout))) {
                String line;
                while ((line = br.readLine()) != null) {
                    teeLog(siteId, "OUT", line);
                    parseStdoutLine(line, siteId);
                }
            }

            try { stderrReader.join(2_000); } catch (InterruptedException ignored) {}
            int exit = process.waitFor();

            Site final_ = loadSite(siteId);
            if (final_ == null) return;
            if (exit == 0 || final_.getNucleis().size() > 0) {
                final_.status = "Finished";
                final_.progress = "100";
                persist(final_, siteId);
                postResultNotification(final_, siteId, true);
            } else {
                final_.status = "Failed";
                final_.progress = "100";
                persist(final_, siteId);
                postResultNotification(final_, siteId, false);
            }
            broadcast(siteId);
        } catch (IOException | InterruptedException e) {
            Log.e("NucleiScanService", "Scan crashed", e);
            Site failed = loadSite(siteId);
            if (failed != null) {
                failed.status = "Failed";
                failed.progress = "100";
                persist(failed, siteId);
                postResultNotification(failed, siteId, false);
                broadcast(siteId);
            }
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
            closeLog(siteId);
            running.remove(siteId);
            severities.remove(siteId);
            stopIfIdle();
        }
    }

    private void openLog(int siteId, String target) {
        try {
            File f = terminalLog(this, siteId);
            f.delete();
            PrintWriter pw = new PrintWriter(new FileWriter(f, true));
            pw.println(stamp() + " [INFO] nuclei scan: " + target);
            pw.flush();
            logWriters.put(siteId, pw);
            broadcast(siteId);
        } catch (IOException e) {
            Log.w("NucleiScanService", "Could not open log for " + siteId + ": " + e.getMessage());
        }
    }

    private void teeLog(int siteId, String stream, String line) {
        PrintWriter pw = logWriters.get(siteId);
        if (pw == null) return;
        pw.println(stamp() + " [" + stream + "] " + line);
        pw.flush();
    }

    private void closeLog(int siteId) {
        PrintWriter pw = logWriters.remove(siteId);
        if (pw != null) {
            pw.println(stamp() + " [INFO] scan ended");
            pw.flush();
            pw.close();
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private java.util.List<String> buildNucleiScript(String target, @Nullable String severityList) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("unset HOME TMPDIR XDG_CACHE_HOME XDG_DATA_HOME XDG_CONFIG_HOME ANDROID_DATA ANDROID_ROOT LD_PRELOAD LD_LIBRARY_PATH");
        lines.add("export HOME=/root");
        lines.add("export TMPDIR=/tmp");
        lines.add("mkdir -p /tmp /root/.config/nuclei");
        StringBuilder cmd = new StringBuilder("/usr/bin/nuclei ");
        cmd.append("-u ").append(shellEscape(target)).append(' ');
        cmd.append("-jsonl -stats -silent -no-color -timeout 8 -retries 1");
        if (severityList != null && !severityList.isEmpty()) {
            cmd.append(" -severity ").append(shellEscape(severityList));
        }
        lines.add(cmd.toString());
        return lines;
    }

    private static String shellEscape(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void readStderr(InputStream stream, int siteId) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                teeLog(siteId, "ERR", line);
                if (line.startsWith("{")) {
                    parseStatsLine(line, siteId);
                } else if (!line.isEmpty()) {
                    Log.d("NucleiScanService", "[" + siteId + "] " + line);
                }
            }
        } catch (IOException e) {
            Log.d("NucleiScanService", "stderr drained: " + e.getMessage());
        }
    }

    private void parseStdoutLine(String line, int siteId) {
        if (line == null || !line.contains("{")) return;
        try {
            int brace = line.indexOf('{');
            String json = line.substring(brace);
            NucleiItem item = NucleiItem.parseItem(json);
            Site site = loadSite(siteId);
            if (site == null) return;
            site.addNuclei(item);
            persist(site, siteId);
            broadcast(siteId);
        } catch (Throwable ignored) {
        }
    }

    private void parseStatsLine(String line, int siteId) {
        try {
            JSONObject o = new JSONObject(line);
            String percent = o.optString("percent", "0");
            Site site = loadSite(siteId);
            if (site == null) return;
            site.progress = percent;
            persist(site, siteId);
            updateForegroundNotification(site);
            broadcast(siteId);
        } catch (JSONException ignored) {
        }
    }

    private void cancelSite(int siteId) {
        Process p = running.remove(siteId);
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
        }
        Site site = loadSite(siteId);
        if (site != null && !"Finished".equals(site.status)) {
            site.status = "Failed";
            site.progress = "100";
            persist(site, siteId);
            broadcast(siteId);
        }
        stopIfIdle();
    }

    private void stopIfIdle() {
        if (running.isEmpty() && queued.isEmpty()) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Nullable
    private Site loadSite(int siteId) {
        java.util.ArrayList<Site> sites = core.getSites();
        if (siteId < 0 || siteId >= sites.size()) return null;
        return sites.get(siteId);
    }

    private void persist(Site site, int siteId) {
        core.changeSiteByPosition(site, siteId);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.nuclei_notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            notificationManager.createNotificationChannel(ch);
        }
    }

    @SuppressLint("InlinedApi")
    private void startInForeground() {
        Notification n = buildForegroundNotification("Starting…", "Spinning up nuclei", 0, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, n);
        }
    }

    private void updateForegroundNotification(Site site) {
        int progress;
        try { progress = Integer.parseInt(site.progress); } catch (NumberFormatException e) { progress = 0; }
        String title = "Scanning " + safeUrl(site.getUrl());
        String text = progress > 0
                ? progress + "% · " + site.getNucleis().size() + " findings"
                : "Loading templates…";
        Notification n = buildForegroundNotification(title, text, progress, progress > 0);
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, n);
    }

    private Notification buildForegroundNotification(String title, String text, int progress, boolean determinate) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.webscan)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (determinate) {
            b.setProgress(100, progress, false);
        } else {
            b.setProgress(100, 0, true);
        }
        return b.build();
    }

    private void postResultNotification(Site site, int siteId, boolean success) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, siteId + 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int icon = success ? R.drawable.done : R.drawable.error;
        String title = success
                ? getString(R.string.nuclei_notif_done, safeUrl(site.getUrl()))
                : getString(R.string.nuclei_notif_failed, safeUrl(site.getUrl()));
        String text = success
                ? site.getNucleis().size() + " findings · severities " + summarizeCounts(site.vulnsCount)
                : "Tap to open the report";
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notificationManager.notify(RESULT_NOTIFICATION_BASE + siteId, n);
    }

    private static String summarizeCounts(int[] counts) {
        if (counts == null || counts.length < 4) return "n/a";
        return "I:" + counts[0] + " L:" + counts[1] + " M:" + counts[2] + " H:" + counts[3];
    }

    private static String safeUrl(String url) {
        if (url == null) return "(unknown)";
        if (url.length() > 60) return url.substring(0, 57) + "…";
        return url;
    }

    private void broadcast(int siteId) {
        Intent i = new Intent(ACTION_UPDATED);
        i.putExtra(EXTRA_SITE_ID, siteId);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private static synchronized long getPidOfProcess(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getLong(p);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        for (Map.Entry<Integer, Process> e : new HashMap<>(running).entrySet()) {
            try { e.getValue().destroy(); } catch (Throwable ignored) {}
            Site s = loadSite(e.getKey());
            if (s != null && !"Finished".equals(s.status)) {
                s.status = "Failed";
                s.progress = "100";
                persist(s, e.getKey());
            }
        }
        running.clear();
        queued.clear();
        for (Integer id : new java.util.ArrayList<>(logWriters.keySet())) closeLog(id);
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
