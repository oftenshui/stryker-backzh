package com.zalexdev.stryker.routerscan;

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
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.routerscan.utils.RouterScanLog;
import com.zalexdev.stryker.routerscan.utils.RsRunner;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RouterScanService extends Service {

    public static final String ACTION_START = "com.zalexdev.stryker.routerscan.START";
    public static final String ACTION_STOP = "com.zalexdev.stryker.routerscan.STOP";
    public static final String ACTION_UPDATED = "com.zalexdev.stryker.routerscan.UPDATED";

    private static final String CHANNEL_ID = "router_scan_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 5090;
    private static final int RESULT_NOTIFICATION_ID = 5091;
    private static final long BROADCAST_THROTTLE_MS = 200L;

    private Core core;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    private Thread coordinator;
    private ExecutorService workers;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile long lastBroadcast;
    private volatile long lastNotify;

    @Override
    public void onCreate() {
        super.onCreate();
        core = new Core(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stryker:routerscan");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopScan();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (RouterScanState.get().running) {
            return START_NOT_STICKY;
        }

        startInForeground();
        if (!wakeLock.isHeld()) wakeLock.acquire(6L * 60 * 60 * 1000);

        cancelled.set(false);
        coordinator = new Thread(this::runBatch, "rs-coordinator");
        coordinator.start();
        return START_NOT_STICKY;
    }

    private void runBatch() {
        RouterScanState st = RouterScanState.get();
        st.running = true;
        broadcast(true);

        List<String> ips = new ArrayList<>(st.ips);
        List<String> ports = new ArrayList<>(st.ports);
        RouterScanLog.info(this, "scan",
                "service batch: " + ips.size() + " ips × " + ports.size() + " ports");

        workers = Executors.newFixedThreadPool(Math.max(1, st.maxThreads), r -> {
            Thread t = new Thread(r, "rs-worker");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        for (String ip : ips) {
            if (cancelled.get()) break;
            for (String port : ports) {
                if (cancelled.get()) break;
                final String fip = ip;
                final String fport = port;
                try {
                    workers.submit(() -> scanHost(fip, fport));
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        workers.shutdown();
        try {
            workers.awaitTermination(7, TimeUnit.DAYS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        finishBatch();
    }

    private void scanHost(String ip, String port) {
        if (cancelled.get()) return;
        RouterScanState st = RouterScanState.get();
        boolean ping;
        String host;
        try {
            ping = core.ping(ip, Integer.parseInt(port), st.timeout);
            host = ip + ":" + port;
        } catch (NumberFormatException e) {
            ping = core.ping(ip, st.timeout);
            host = ip;
        }
        st.incCompleted();

        if (ping && !cancelled.get()) {
            st.incResponsive();
            Router temp = new Router();
            temp.setIp(host);
            temp.setStatus(getString(R.string.rs_status_scanning));
            st.results.add(temp);
            broadcast(false);

            Router res = RsRunner.run(this, host);
            temp.setScanned(true);
            if (res.getSuccess()) {
                temp.setType(1);
                temp.setSuccess(true);
                temp.setSsid(res.getSsid());
                temp.setPsk(res.getPsk());
                temp.setAuth(res.getAuth());
                temp.setStatus(getString(R.string.rs_status_cracked));
                st.incSuccess();
            } else {
                temp.setType(2);
                temp.setStatus(getString(R.string.rs_status_done));
            }
            broadcast(false);
        }
        updateForegroundNotification(false);
    }

    private void finishBatch() {
        RouterScanState st = RouterScanState.get();
        st.running = false;
        if (workers != null) workers.shutdownNow();
        updateForegroundNotification(true);
        postResultNotification(st);
        broadcast(true);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopScan() {
        cancelled.set(true);
        if (workers != null) workers.shutdownNow();
    }

    private void broadcast(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastBroadcast < BROADCAST_THROTTLE_MS) return;
        lastBroadcast = now;
        Intent i = new Intent(ACTION_UPDATED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.rs_notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            notificationManager.createNotificationChannel(ch);
        }
    }

    @SuppressLint("InlinedApi")
    private void startInForeground() {
        Notification n = buildForegroundNotification(0, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, n);
        }
    }

    private void updateForegroundNotification(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastNotify < 600L) return;
        lastNotify = now;
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID,
                buildForegroundNotification(RouterScanState.get().percent(), false));
    }

    private Notification buildForegroundNotification(int percent, boolean indeterminate) {
        RouterScanState st = RouterScanState.get();
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, RouterScanService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = getString(R.string.rs_notif_progress,
                percent, st.getResponsive(), st.getSuccess());

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.router)
                .setContentTitle(getString(R.string.rs_notif_title))
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.stop), stopPi);
        b.setProgress(100, percent, indeterminate);
        return b.build();
    }

    private void postResultNotification(RouterScanState st) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 2, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.router)
                .setContentTitle(getString(R.string.rs_notif_done_title))
                .setContentText(getString(R.string.rs_notif_done_text,
                        st.getSuccess(), st.getResponsive()))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notificationManager.notify(RESULT_NOTIFICATION_ID, n);
    }

    @Override
    public void onDestroy() {
        cancelled.set(true);
        if (workers != null) workers.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        RouterScanState.get().running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
