package com.zalexdev.stryker.localnetwork.utils;

import static com.zalexdev.stryker.utils.Core.EXECUTE;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NmapReportGenerator extends IntentService {


    public String ip = "192.168.1.1";
    public Process process;
    public int notificationId = 0;
    public boolean isRunning = false;


    public NmapReportGenerator() {
        super("NmapReportGenerator");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            notificationId++;
            ip = intent.getStringExtra("ip");
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "NmapReporter")
                    .setSmallIcon(R.drawable.bolt)
                    .setContentTitle("Scanning " + ip)
                    .setContentText("Please wait...")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Launching nmap on " + ip + "..."))
                    .setAutoCancel(true)
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);

            String CHANNEL_ID = "NmapReporter";
            NotificationChannel notificationChannel;
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notificationChannel = new NotificationChannel(CHANNEL_ID, "NmapReporter", NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(notificationChannel);
            }

            notificationManager.notify(notificationId, builder.build());
            startNmap(builder);

        }
    }



    public void startNmap(NotificationCompat.Builder builder) {
        isRunning = true;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ArrayList<String> output = new ArrayList<>();
        try {
            process = Runtime.getRuntime().exec("su -mm");
            try {
                OutputStream stdin = process.getOutputStream();
                InputStream stderr = process.getErrorStream();
                InputStream stdout = process.getInputStream();
                stdin.write((EXECUTE+ "'nmap "+ip+" -A --script=vuln --stats-every 1s -Pn'"+ '\n').getBytes());
                stdin.write(("\n").getBytes());
                stdin.write(("\n").getBytes());
                stdin.flush();
                stdin.close();
                BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
                String line;
                while ((line = br.readLine()) != null) {
                    builder.setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(line));
                    notificationManager.notify(notificationId, builder.build());
                    if (!line.contains("done") && !line.contains("elapsed")) {output.add(line);}
                    Matcher per = Pattern.compile("[0-9]*\\.[0-9]+%").matcher(line);
                    if (per.find()){
                        builder.setProgress(100,(int) Double.parseDouble(per.group().replace("%","")) , false);
                    }
                }
                br.close();
                BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
                String lineerror;
                while ((lineerror = br2.readLine()) != null) {
                    output.add(lineerror);
                }
                br2.close();
                process.waitFor();
                process.destroy();
                isRunning = false;
                new Logger().generateNmapReport(ip,output);
                notificationManager.cancel(notificationId);
                sendSuccessNotification();
            } catch (IOException e) {
            }
        }catch (Exception ignored){

        }

    }

    @Override
    public void onDestroy() {
        if (isRunning){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "NmapReporter")
                .setSmallIcon(R.drawable.error)
                .setContentTitle("Error with scanning " + ip)
                .setContentText("App was closed by android doze mode!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);

        String CHANNEL_ID = "NmapReporter";
        NotificationChannel notificationChannel;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ID, "NmapReporter", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        notificationManager.notify(100, builder.build());}
        super.onDestroy();
    }
    public void sendSuccessNotification(){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "NmapReporter")
                .setSmallIcon(R.drawable.done)
                .setContentTitle("Report for " + ip)
                .setContentText("Scanning is done! Please check report in folder!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);

        String CHANNEL_ID = "NmapReporter";
        NotificationChannel notificationChannel;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ID, "NmapReporter", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        notificationManager.notify(200, builder.build());
    }
}