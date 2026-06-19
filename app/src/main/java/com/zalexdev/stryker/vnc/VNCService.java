package com.zalexdev.stryker.vnc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class VNCService extends Service {
    private final String CHANNEL_ID = "VNCChannel";
    public static final String ACTION_START = "com.zalexdev.stryker.vnc.action.START";
    public static final String ACTION_STOP = "com.zalexdev.stryker.vnc.action.STOP";
    public static final String EXTRA_RESOLUTION = "com.zalexdev.stryker.vnc.extra.resolution";
    public static final String EXTRA_PORT = "com.zalexdev.stryker.vnc.extra.port";
    private Timer timer = new Timer();
    private String port = "";
    private Process vnc = null;
    private Core core = null;
    private NotificationCompat.Builder notification;

    @Override
    public void onCreate() {
        core = new Core(this);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        createNotificationChannel();
        Intent notificationPendingIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationPendingIntent, Utils.setPendingIntentFlag());

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VNC Server")
                .setContentText("Running command. Waiting for connection...")
                .setSmallIcon(R.drawable.vnc)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent);

        startForeground(33, notification.build());
        if (action != null) {
            if (action.equals(ACTION_START)) {
                port = intent.getStringExtra(EXTRA_PORT);
                final String param1 = intent.getStringExtra(EXTRA_RESOLUTION);
                final String param2 = intent.getStringExtra(EXTRA_PORT);
                new Thread(() -> {
                    try {
                        if (!isVNCStarted()) {
                            startVNC(param1, param2);
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        checkVNC();
                    }
                }, 0, 10000);
            } else if (action.equals(ACTION_STOP)) {
                new Thread(() -> {
                    try {
                        stopVNC();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "VNCServiceChannel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public void startVNC(String resolution, String port) throws IOException, InterruptedException {
        if (vnc != null) {
            vnc.destroy();
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_START);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(intent);

        vnc = core.generateSuProcess();
        OutputStream input = vnc.getOutputStream();
        input.write(("/data/data/com.zalexdev.stryker/files/busybox chroot /data/local/stryker/release /usr/bin/sudo -E PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH /bin/su\n").getBytes());
        input.write(("vncserver-start -p " + port + " -r " + resolution + "\n").getBytes());
        input.flush();
        vnc.waitFor();
    }

    public void stopVNC() throws IOException {
        if (vnc != null) {
            vnc.destroy();
        }

        Intent intent = new Intent();
        intent.setAction(ACTION_STOP);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(intent);

        vnc = core.generateSuProcess();
        OutputStream input = vnc.getOutputStream();
        InputStream output = vnc.getInputStream();
        InputStream error = vnc.getErrorStream();
        ArrayList<String> outputList = new ArrayList<>();
        input.write(("/data/data/com.zalexdev.stryker/files/busybox chroot /data/local/stryker/release /usr/bin/sudo -E PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH /bin/su\n").getBytes());
        input.write(("vncserver-stop\n").getBytes());
        input.write(("exit\n").getBytes());
        input.write(("exit\n").getBytes());
        input.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(output));
        String line;
        while ((line = reader.readLine()) != null) {
            outputList.add(line);
        }
        reader = new BufferedReader(new InputStreamReader(error));
        while ((line = reader.readLine()) != null) {
            outputList.add(line);
        }

        vnc.destroy();
        timer.cancel();
        stopForeground(false);
        stopSelf();
    }

    private boolean isVNCStarted() {
        return !core.customChrootCommand("pidof Xvfb").isEmpty();
    }

    public void checkVNC() {
        if (isVNCStarted()) {
            notification.setContentText("Running VNC Server on localhost:" + port);
            notification.build();
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(33, notification.build());
        }
    }
}