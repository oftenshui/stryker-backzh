package com.zalexdev.stryker.macchanger;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.AdvancedProcess;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.SimpleProcess;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MACChangerInline extends AppCompatActivity {

    private Core core;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_macchanger_inline);
        CharSequence mac = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        core = new Core(this);

        Matcher m = Pattern.compile("(([0-9A-f]{2}:){5}[0-9A-f]{2})").matcher(mac);
        if (m.find()) {
            mac = m.group().toUpperCase();
        } else {
            core.toaster("No valid MAC Address found!");
            finish();
            return;
        }


        TextView old_mac = findViewById(R.id.old_mac);
        TextView new_mac = findViewById(R.id.new_mac);
        View exit = findViewById(R.id.exit);
        if (exit != null) exit.setOnClickListener(v -> finish());
        String iface = core.getString("wlan_wifi");

        new SimpleProcess(this, "ip addr show " + iface + " | sed -n \"s/.*link\\/ether \\(\\([0-9A-f]\\{2\\}:\\)\\{5\\}[0-9A-f]\\{2\\}\\).*/\\1/p\"", false) {
            @Override
            public void onFinished(ArrayList<String> outputList) {
                if (outputList.size() > 0) {
                    old_mac.setText(outputList.get(0));
                }
            }
        };
        new_mac.setText(mac);
        String finalMac = mac.toString();
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R) {
            new AdvancedProcess(this, this, "/data/data/com.zalexdev.stryker/files/changemac "
                    + iface
                    + " "
                    + finalMac, false) {

                @Override
                public void onFinished(ArrayList<String> outputList) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        core.toaster(activity, "Need to connect to network. Waiting 10 seconds...");
                        new Thread(() -> {
                            long end = System.currentTimeMillis() + 10000;
                            while (System.currentTimeMillis() < end) {
                                if (isNetworkAvailable()) {
                                    String macOnUp = core.customCommand("ip addr show " + iface + " | sed -n \"s/.*link\\/ether \\(\\([0-9A-f]\\{2\\}:\\)\\{5\\}[0-9A-f]\\{2\\}\\).*/\\1/p\"").get(0);
                                    activity.runOnUiThread(() -> {
                                        if (!finalMac.equalsIgnoreCase(macOnUp)) {
                                            View view = getLayoutInflater().inflate(R.layout.fragment_macchanger_a12_dialog, null);
                                            TextView message = view.findViewById(R.id.message);
                                            message.setText(Html.fromHtml("Failed to change the MAC address on your device. The Android version of your device is greater than 11, in which case you need to use XPosed (or <a href=\"https://github.com/LSPosed/LSPosed#install\">LSPosed</a>) and the <a href=\"https://github.com/DavidBerdik/MACsposed\">MACsposed</a> module. More details <a href=\"https://github.com/DavidBerdik/MACsposed\">here</a>.", Html.FROM_HTML_MODE_LEGACY));
                                            message.setMovementMethod(new LinkMovementMethod());
                                            new MaterialAlertDialogBuilder(context)
                                                    .setTitle("Failed to change MAC address.")
                                                    .setView(view)
                                                    .setPositiveButton(android.R.string.ok, (di, i) -> {
                                                    })
                                                    .setCancelable(true)
                                                    .show();
                                        } else {
                                            core.toaster("MAC address successful changed.");
                                        }
                                    });
                                    return;
                                }
                            }
                            core.toaster("The network wait time was longer than expected.");
                        }).start();
                    } else {
                        if (Core.contains(outputList, "successfully changed")) {
                            core.toaster("MAC address successful changed.");
                        } else {
                            core.toaster("Failed to change MAC address.");
                        }
                    }
                    finish();
                }

                @Override
                public void onNewLine(String line) {
                    if (line.contains("MAC address format error")) {
                        core.toaster("MAC address format error.");
                        process.destroy();
                    } else if (line.contains("Changing MAC address")) {
                        core.toaster("Changing MAC address...");
                    } else if (line.contains("Wait about 5")) {
                        core.toaster(line.substring(4));
                    } else if (line.contains("Failed to change")) {
                        process.destroy();
                    } else if (line.contains("successfully changed")) {
                        process.destroy();
                    } else if (line.contains("address changed")) {
                        process.destroy();
                    }
                }

                @Override
                public void onEvent(String line) {

                }
            };
        } else {
            new Thread(() -> {
                core.customCommand("ip link set " + iface + " down", true);
                new SimpleProcess(this, "macchanger " + iface + " -m " + finalMac, true) {
                    @Override
                    public void onFinished(ArrayList<String> outputList) {
                        if (Core.contains(outputList, "New MAC:")) {
                            core.toaster("MAC address was successful changed.");
                        } else {
                            core.toaster("Failed to change MAC address.");
                        }
                        new Thread(() -> core.customCommand("ip link set " + iface + " up")).start();
                        finish();
                    }
                };
            }).start();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null
                && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }
}