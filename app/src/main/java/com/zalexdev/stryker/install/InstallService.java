package com.zalexdev.stryker.install;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class InstallService extends Service {

    public static final String TOOL_METASPLOIT = "metasploit";
    public static final String TOOL_NUCLEI = "nuclei";
    public static final String TOOL_HYDRA = "hydra";

    public static final String ACTION_INSTALL = "com.zalexdev.stryker.install.INSTALL";
    public static final String ACTION_CANCEL = "com.zalexdev.stryker.install.CANCEL";
    public static final String ACTION_UPDATED = "com.zalexdev.stryker.install.UPDATED";

    public static final String EXTRA_TOOL = "tool";
    public static final String EXTRA_LINE = "line";
    public static final String EXTRA_STATUS = "status";

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    private static final String CHANNEL_ID = "stryker_install_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 5100;

    private static final long MAX_LOG_FILE_BYTES = 256 * 1024;
    private static final int MAX_REPLAY_LINES = 600;
    private static final long PROGRESS_BROADCAST_MS = 150;
    private volatile long lastProgressBroadcast = 0;

    private Core core;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private ExecutorService executor;

    private final AtomicReference<String> activeTool = new AtomicReference<>(null);
    private volatile Process current;

    public static void start(Context ctx, String tool) {
        Intent i = new Intent(ctx, InstallService.class)
                .setAction(ACTION_INSTALL)
                .putExtra(EXTRA_TOOL, tool);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void cancel(Context ctx, String tool) {
        Intent i = new Intent(ctx, InstallService.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_TOOL, tool);
        try {
            ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    public static String statusOf(Core core, String tool) {
        String s = core.getString(statusKey(tool));
        return (s == null || s.isEmpty()) ? STATUS_IDLE : s;
    }

    public static boolean isRunning(Core core, String tool) {
        return STATUS_RUNNING.equals(statusOf(core, tool));
    }

    public static boolean isServiceAlive(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        try {
            for (ActivityManager.RunningServiceInfo si : am.getRunningServices(Integer.MAX_VALUE)) {
                if (InstallService.class.getName().equals(si.service.getClassName())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String reconcileStatus(Context ctx, Core core, String tool) {
        String s = statusOf(core, tool);
        if (STATUS_RUNNING.equals(s) && !isServiceAlive(ctx)) {
            core.putString(statusKey(tool), STATUS_FAILED);
            return STATUS_FAILED;
        }
        return s;
    }

    public static File logFile(Context ctx, String tool) {
        File dir = new File(ctx.getFilesDir(), "install-logs");
        if (!dir.exists())
            dir.mkdirs();
        return new File(dir, tool + ".log");
    }

    public static List<String> readLog(Context ctx, String tool) {
        File f = logFile(ctx, tool);
        if (!f.exists()) return new ArrayList<>();
        ArrayDeque<String> tail = new ArrayDeque<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f)))) {
            String line;
            while ((line = br.readLine()) != null) {
                tail.addLast(line);
                if (tail.size() > MAX_REPLAY_LINES) tail.removeFirst();
            }
        } catch (Exception e) {
            Log.w("InstallService", "readLog failed: " + e.getMessage());
        }
        return new ArrayList<>(tail);
    }

    private static String statusKey(String tool) {
        return "install_status_" + tool;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        core = new Core(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stryker-install-worker");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stryker:install");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        String tool = intent != null ? intent.getStringExtra(EXTRA_TOOL) : null;

        String running = activeTool.get();
        startInForeground(running != null ? running : tool, getString(R.string.install_notif_starting));

        if (intent == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            cancelActive(tool);
            return START_NOT_STICKY;
        }
        if (!ACTION_INSTALL.equals(action) || !isKnownTool(tool)) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (!activeTool.compareAndSet(null, tool)) {
            broadcast(activeTool.get(), null, statusOf(core, activeTool.get()));
            return START_NOT_STICKY;
        }
        if (!wakeLock.isHeld()) wakeLock.acquire(2L * 60 * 60 * 1000);

        executor.submit(() -> {
            try {
                runInstall(tool);
            } finally {
                activeTool.set(null);
                stopIfIdle();
            }
        });
        return START_NOT_STICKY;
    }

    private boolean isKnownTool(String tool) {
        return TOOL_METASPLOIT.equals(tool) || TOOL_NUCLEI.equals(tool) || TOOL_HYDRA.equals(tool);
    }

    private void runInstall(String tool) {
        setStatus(tool, STATUS_RUNNING);
        truncateLog(tool);
        tee(tool, "Starting " + label(tool) + " installation");
        updateNotification(tool, getString(R.string.install_notif_running, label(tool)));

        Process process = null;
        boolean shellOk = false;
        try {
            process = core.generateSuProcess();
            current = process;
            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();

            final Process fp = process;
            Thread errReader = new Thread(() -> drain(fp.getErrorStream(), tool), "install-stderr-" + tool);
            errReader.start();

            stdin.write((Core.EXECUTE + "ash\n").getBytes());
            for (String line : commandsFor(tool)) {
                stdin.write((line + "\n").getBytes());
            }
            stdin.write("exit\nexit\n".getBytes());
            stdin.flush();
            stdin.close();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(stdout))) {
                String line;
                while ((line = br.readLine()) != null) {
                    handleLine(tool, line.trim());
                }
            }
            try { errReader.join(2000); } catch (InterruptedException ignored) {}
            process.waitFor();
            shellOk = true;
        } catch (Exception e) {
            Log.e("InstallService", "install shell crashed", e);
            tee(tool, "[E] install shell crashed: " + e.getMessage());
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Throwable ignored) {}
            }
            current = null;
        }

        boolean ok = shellOk && verify(tool);
        if (ok) {
            markInstalled(tool);
            tee(tool, "OK: " + label(tool) + " installation complete");
            setStatus(tool, STATUS_DONE);
            updateNotification(tool, getString(R.string.install_notif_done, label(tool)));
        } else {
            setStatus(tool, STATUS_FAILED);
            tee(tool, "[E] " + label(tool) + " install did not verify");
            updateNotification(tool, getString(R.string.install_notif_failed, label(tool)));
        }
    }

    private void handleLine(String tool, String line) {
        if (line == null || line.isEmpty()) return;
        if (isProgressLine(line)) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastProgressBroadcast >= PROGRESS_BROADCAST_MS) {
                lastProgressBroadcast = now;
                broadcast(tool, line, statusOf(core, tool));
            }
        } else {
            tee(tool, line);
        }
        if (line.contains("×")) {
            updateNotification(tool, line.replace("×", "").trim());
        }
    }

    private static boolean isProgressLine(String line) {
        return line.contains("Receiving objects") || line.contains("Resolving deltas")
                || line.contains("Counting objects") || line.contains("Compressing objects")
                || line.contains("Updating files") || line.contains("Unpacking objects");
    }

    private void drain(InputStream stream, String tool) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (!t.isEmpty()) handleLine(tool, "[E] " + t);
            }
        } catch (Exception e) {
            Log.d("InstallService", "stderr drained: " + e.getMessage());
        }
    }

    private boolean verify(String tool) {
        try {
            switch (tool) {
                case TOOL_NUCLEI: {
                    ArrayList<String> probe = core.customChrootCommand("/usr/bin/nuclei -version 2>&1 | head -3", true);
                    return Core.contains(probe, "nuclei") || core.checkFile("/data/local/stryker/release/usr/bin/nuclei");
                }
                case TOOL_HYDRA: {
                    ArrayList<String> probe = core.customChrootCommand("/usr/bin/hydra -h 2>&1 | head -3", true);
                    return Core.contains(probe, "Hydra v") || Core.contains(probe, "by van Hauser")
                            || core.checkFile("/data/local/stryker/release/usr/bin/hydra");
                }
                case TOOL_METASPLOIT:
                default:
                    return core.checkFile("/data/local/stryker/release/metasploit-framework/msfconsole");
            }
        } catch (Throwable t) {
            Log.w("InstallService", "verify failed: " + t.getMessage());
            return false;
        }
    }

    private void markInstalled(String tool) {
        switch (tool) {
            case TOOL_METASPLOIT: core.putBoolean("msf", true); break;
            case TOOL_NUCLEI: core.putBoolean("nuclei", true); break;
            case TOOL_HYDRA: core.putBoolean("hydra", true); break;
        }
        if (!core.checkModule(tool)) core.installModule(tool);
    }

    private List<String> commandsFor(String tool) {
        switch (tool) {
            case TOOL_NUCLEI: return nucleiCommands();
            case TOOL_HYDRA: return hydraCommands();
            case TOOL_METASPLOIT:
            default: return metasploitCommands();
        }
    }

    private List<String> metasploitCommands() {
        ArrayList<String> c = new ArrayList<>();
        c.add("echo ×Updating packages");
        c.add("apk update");
        c.add("echo ×Installing additional pkgs");
        c.add("apk upgrade");
        c.add("apk add ruby-dev libffi-dev openssl-dev readline-dev sqlite-dev autoconf bison libxml2-dev postgresql-dev libpcap-dev yaml-dev subversion git sqlite ruby-bundler zlib-dev ruby-nokogiri ruby-bigdecimal ncurses ncurses-dev nmap make gcc musl-dev git g++ libxslt-dev");
        c.add("echo ×Downloading metasploit. It can take a while...");
        c.add("rm -rf /metasploit-framework");
        c.add("git clone --depth 1 https://github.com/rapid7/metasploit-framework");
        c.add("echo ×Pulling msfpc helper");
        c.add("rm -rf /msfpc");
        c.add("git clone https://github.com/g0tmi1k/msfpc --progress");
        c.add("cp /msfpc/msfpc.sh /metasploit-framework/msfpc");
        c.add("echo ×Linking binaries");
        c.add("cp /metasploit-framework/msfvenom /usr/bin/msfvenom");
        c.add("chmod 777 /metasploit-framework/msfpc");
        c.add("chmod 777 /usr/bin/msfvenom");
        c.add("cp -R /metasploit-framework/* /usr/bin/");
        c.add("echo ×Installing msf pkgs and tools. It can take a while");
        c.add("mkdir -p /tmp/stryker_temp");
        c.add("export TMPDIR=/tmp/stryker_temp && export TEMP=/tmp/stryker_temp && export TMP=/tmp/stryker_temp && gem install bundler");
        c.add("bundle config build.nokogiri --use-system-libraries");
        c.add("cd /metasploit-framework && bundle install");
        c.add("bundle install --gemfile /usr/bin/Gemfile");
        c.add("bundle install --gemfile /metasploit-framework/Gemfile");
        c.add("echo ×Initializing metasploit...");
        c.add("./msfconsole");
        c.add("echo ×Making sure everything is ready");
        c.add("bundle install --gemfile /metasploit-framework/Gemfile");
        return c;
    }

    private List<String> nucleiCommands() {
        ArrayList<String> c = new ArrayList<>();
        c.add("echo ×Prepare clean Go environment");
        c.add("unset HOME TMPDIR XDG_CACHE_HOME XDG_DATA_HOME XDG_CONFIG_HOME XDG_RUNTIME_DIR ANDROID_DATA ANDROID_ROOT ANDROID_STORAGE EXTERNAL_STORAGE LD_PRELOAD LD_LIBRARY_PATH");
        c.add("export HOME=/root");
        c.add("export TMPDIR=/tmp");
        c.add("export GOPATH=/root/go");
        c.add("export GOCACHE=/root/.cache/go-build");
        c.add("export GOMODCACHE=/root/go/pkg/mod");
        c.add("export PATH=/root/go/bin:/usr/local/go/bin:$PATH");
        c.add("export CGO_ENABLED=0");
        c.add("mkdir -p /tmp /root/go /root/.cache");
        c.add("chmod 1777 /tmp");
        c.add("echo ×Refresh apk index");
        c.add("apk update");
        c.add("echo ×Install Go toolchain");
        c.add("apk add --no-cache go git ca-certificates");
        c.add("echo ×go install nuclei@latest");
        c.add("go install -v github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest");
        c.add("echo ×Deploy nuclei to /usr/bin");
        c.add("install -m 0755 /root/go/bin/nuclei /usr/bin/nuclei");
        c.add("echo ×Verify nuclei -version");
        c.add("/usr/bin/nuclei -version");
        c.add("echo ×Done");
        return c;
    }

    private List<String> hydraCommands() {
        ArrayList<String> c = new ArrayList<>();
        c.add("echo ×Refreshing apk index");
        c.add("apk update");
        c.add("echo ×Installing hydra");
        c.add("apk add --no-cache hydra");
        c.add("echo ×Done apk");
        return c;
    }

    private String label(String tool) {
        switch (tool) {
            case TOOL_NUCLEI: return "Nuclei";
            case TOOL_HYDRA: return "Hydra";
            case TOOL_METASPLOIT: default: return "Metasploit";
        }
    }

    private void truncateLog(String tool) {
        File f = logFile(this, tool);
        f.delete();
    }

    private synchronized void tee(String tool, String line) {
        File f = logFile(this, tool);
        if (line.contains("×") || f.length() <= MAX_LOG_FILE_BYTES) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                pw.println(line);
            } catch (Exception e) {
                Log.w("InstallService", "tee failed: " + e.getMessage());
            }
        }
        broadcast(tool, line, statusOf(core, tool));
    }

    private void setStatus(String tool, String status) {
        core.putString(statusKey(tool), status);
        broadcast(tool, null, status);
    }

    private void broadcast(String tool, @Nullable String line, String status) {
        Intent i = new Intent(ACTION_UPDATED).setPackage(getPackageName());
        i.putExtra(EXTRA_TOOL, tool);
        if (line != null) i.putExtra(EXTRA_LINE, line);
        i.putExtra(EXTRA_STATUS, status);
        sendBroadcast(i);
    }

    private void cancelActive(String tool) {
        Process p = current;
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
        }
        if (tool != null) {
            setStatus(tool, STATUS_FAILED);
            tee(tool, "[E] install cancelled");
        }
        activeTool.set(null);
        stopIfIdle();
    }

    private void stopIfIdle() {
        if (activeTool.get() == null) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.install_notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            notificationManager.createNotificationChannel(ch);
        }
    }

    @SuppressLint("InlinedApi")
    private void startInForeground(String tool, String text) {
        Notification n = buildNotification(label(tool), text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, n);
        }
    }

    private void updateNotification(String tool, String text) {
        if (notificationManager == null) return;
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(label(tool), text));
    }

    private Notification buildNotification(String toolLabel, String text) {
        Intent openApp = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.download)
                .setContentTitle(getString(R.string.install_notif_title, toolLabel))
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Process p = current;
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
        }
        String tool = activeTool.get();
        if (tool != null && STATUS_RUNNING.equals(statusOf(core, tool))) {
            setStatus(tool, STATUS_FAILED);
        }
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
