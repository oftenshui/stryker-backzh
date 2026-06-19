package com.zalexdev.stryker.dashboard;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.arsenal.ArsenalFragment;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;

public class Dashboard extends Fragment {

    private Activity activity;
    private Context context;
    private Core core;
    private final MainActivity.Receiver receiver = new MainActivity.Receiver();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @SuppressLint({"SetTextI18n", "SdCardPath"})
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        TextView userHello = view.findViewById(R.id.user_hello);
        TextView userSubtitle = view.findViewById(R.id.user_subtitle);

        LinearLayout menuWifi = view.findViewById(R.id.menu_wifi);
        LinearLayout menuLocalNetwork = view.findViewById(R.id.menu_localnetwork);
        LinearLayout menuHs = view.findViewById(R.id.menu_hs);
        LinearLayout menuExploits = view.findViewById(R.id.menu_exloits);
        LinearLayout menuSploit = view.findViewById(R.id.menu_sploit);
        LinearLayout menuNuclei = view.findViewById(R.id.menu_nuclei);
        LinearLayout menuRouterScan = view.findViewById(R.id.menu_router_scan);
        LinearLayout menuGeo = view.findViewById(R.id.menu_geo);
        LinearLayout menuMsf = view.findViewById(R.id.menu_msf);

        LinearLayout terminal = view.findViewById(R.id.terminal);
        LinearLayout news = view.findViewById(R.id.news);
        LinearLayout wifiHistory = view.findViewById(R.id.wifi_history);
        LinearLayout recentScan = view.findViewById(R.id.recent_scan);
        TextView savedCount = view.findViewById(R.id.saved_count);
        TextView recentScanCount = view.findViewById(R.id.recent_scan_count);
        TextView recentScanSubtitle = view.findViewById(R.id.recent_scan_subtitle);

        checkPermission();
        core.putInt("dashboard_open", core.getInt("dashboard_open") + 1);

        renderHero(userHello, userSubtitle, savedCount, recentScanCount, recentScanSubtitle);

        menuWifi.setOnClickListener(v -> receiver.changeFragment(R.id.wifi_item));
        menuLocalNetwork.setOnClickListener(v -> receiver.changeFragment(R.id.lan_item));
        menuHs.setOnClickListener(v -> receiver.changeFragment(R.id.hs_item));
        menuExploits.setOnClickListener(v -> receiver.changeFragment(
                R.id.arsenal_item, ArsenalFragment.forTab(ArsenalFragment.TAB_HUB)));
        menuRouterScan.setOnClickListener(v -> receiver.changeFragment(R.id.router_scan_item));
        menuNuclei.setOnClickListener(v -> receiver.changeFragment(R.id.nuclei_item));
        menuSploit.setOnClickListener(v -> receiver.changeFragment(
                R.id.arsenal_item, ArsenalFragment.forTab(ArsenalFragment.TAB_DB)));
        menuMsf.setOnClickListener(v -> receiver.changeFragment(R.id.metasploit_item));
        menuGeo.setOnClickListener(v -> receiver.changeFragment(R.id.geomac_item));

        terminal.setOnClickListener(v -> openTerminal());

        news.setOnClickListener(v -> receiver.changeFragment(R.id.dasboard_item, new NewsFragment(), "news"));
        wifiHistory.setOnClickListener(v -> receiver.changeFragment(R.id.dasboard_item, new WiFiHistoryFragment(), "wifi_history"));
        recentScan.setOnClickListener(v -> receiver.changeFragment(R.id.lan_item));

        MaterialCardView magiskNotification = view.findViewById(R.id.magisk);
        new Thread(() -> {
            if (core.checkMagiskNotification() && !core.getBoolean("magisk_notif")) {
                activity.runOnUiThread(() -> magiskNotification.setVisibility(View.VISIBLE));
            }
        }).start();

        MaterialButton magiskYes = view.findViewById(R.id.magisk_yes);
        MaterialButton magiskNo = view.findViewById(R.id.magisk_no);
        magiskYes.setOnClickListener(v -> {
            magiskNotification.setVisibility(View.GONE);
            core.disableMagiskNotification();
            if (!core.checkMagiskNotification()) {
                core.toaster(getString(R.string.magisk_off_success));
            } else {
                core.toaster(getString(R.string.magisk_notif_bad));
            }
            core.putBoolean("magisk_notif", true);
        });
        magiskNo.setOnClickListener(v -> {
            magiskNotification.setVisibility(View.GONE);
            core.putBoolean("magisk_notif", true);
        });

        if (!core.getBoolean("exploits_v30")) {
            new Thread(() -> {
                if (!core.checkFile("/sdcard/Stryker/exploits/checker.py")) {
                    Snackbar s = Snackbar.make(activity.findViewById(android.R.id.content), "Updating please wait...", 60000);
                    activity.runOnUiThread(s::show);
                    core.deleteFile("/sdcard/Stryker/exploits/");
                    core.copyFile("/data/local/stryker/release/exploits/", "/sdcard/Stryker/exploits");
                    core.copyFile("/data/data/com.zalexdev.stryker/files/checker.py", "/data/local/stryker/release/exploits/checker.py");
                    activity.runOnUiThread(s::dismiss);
                    core.putListString("installed_modules", new ArrayList<>());
                }
                core.putBoolean("exploits_v30", true);
            }).start();
        }

        showFirstScanTip(menuWifi);
    }

    private void renderHero(TextView hello, TextView subtitle, TextView savedCount, TextView recentScanCount, TextView recentScanSubtitle) {
        String username = core.getString("username");
        if (username == null || username.isEmpty() || username.equals("User")) {
            hello.setText(getString(R.string.dashboard_hello));
        } else {
            hello.setText(getString(R.string.dashboard_hello) + ", " + username);
        }

        int saved = core.getSavedNetworks().size();
        int lastScan = core.getLastNetworkScan().size();

        if (saved == 0 && lastScan == 0) {
            subtitle.setText(R.string.dashboard_subtitle_default);
        } else {
            subtitle.setText(getString(R.string.dashboard_subtitle_stats, saved, lastScan));
        }

        savedCount.setText(String.valueOf(saved));
        if (lastScan > 0) {
            recentScanCount.setVisibility(View.VISIBLE);
            recentScanCount.setText(String.valueOf(lastScan));
            recentScanSubtitle.setText(getString(R.string.dashboard_card_recentscan_subtitle));
        } else {
            recentScanCount.setVisibility(View.GONE);
            recentScanSubtitle.setText(getString(R.string.dashboard_card_recentscan_empty));
        }
    }

    private void openTerminal() {
        Intent terminal = new Intent(context, com.stryker.terminal.ui.term.NeoTermActivity.class);
        terminal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(terminal);
    }

    private void showFirstScanTip(View target) {
        if (core.getInt("dashboard_open") == 12 && !core.getBoolean("firstscan")) {
            TapTargetView.showFor(activity,
                    TapTarget.forView(target, "Tip: Networks with ⭐",
                                    "Networks with ⭐ are likely vulnerable to Pixie Dust")
                            .outerCircleColor(R.color.stryker_accent)
                            .outerCircleAlpha(0.96f)
                            .targetCircleColor(android.R.color.white)
                            .titleTextSize(20)
                            .titleTextColor(android.R.color.white)
                            .descriptionTextSize(16)
                            .descriptionTextColor(android.R.color.white)
                            .textColor(android.R.color.white)
                            .dimColor(android.R.color.black)
                            .drawShadow(true)
                            .cancelable(true)
                            .tintTarget(true)
                            .transparentTarget(true)
                            .targetRadius(60));
            core.putBoolean("firstscan", true);
        }
    }

    private void checkPermission() {
        if (context.checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{WRITE_EXTERNAL_STORAGE},
                    123
            );
        }
    }
}
