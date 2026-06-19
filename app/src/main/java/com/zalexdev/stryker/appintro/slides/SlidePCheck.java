package com.zalexdev.stryker.appintro.slides;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.race604.drawable.wave.WaveDrawable;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.AppIntroActivity;
import com.zalexdev.stryker.utils.Core;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlidePCheck extends Fragment {

    private static final String PREF_CHECKED = "pcheck_checked";
    private static final String PREF_ROOT = "pcheck_root";
    private static final String PREF_MON = "pcheck_monitor";
    private static final String PREF_USB = "pcheck_usb";
    private static final String PREF_MANUFACT = "pcheck_manufacture";
    private static final String PREF_SPACE = "pcheck_space";
    private static final String PREF_FREE_GB = "pcheck_free_gb";

    private Activity activity;
    private Context context;
    private Core core;
    private ViewPager2 mPager;
    private MaterialCardView cardView;
    private TextView capabilitiesLabel;
    private TextView disclaimer;
    private MaterialButton button;
    private LinearProgressIndicator progressIndicator;

    private TextView rootSub, rootBadge;
    private TextView monMode, monBadge;
    private TextView usbOtg, usbBadge;
    private TextView manufacture, manufactureBadge;
    private TextView spaceSub, spaceBadge;

    private boolean checked = false;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_slide_pcheck, container, false);
        activity = getActivity();
        if (activity == null) return view;
        context = getContext();
        core = new Core(context);
        mPager = activity.findViewById(R.id.view_pager);

        cardView = view.findViewById(R.id.check_card);
        capabilitiesLabel = view.findViewById(R.id.capabilities_label);
        disclaimer = view.findViewById(R.id.disclaimer);
        button = view.findViewById(R.id.login);
        progressIndicator = view.findViewById(R.id.progress_indicator);

        rootSub = view.findViewById(R.id.isRootOk);
        rootBadge = view.findViewById(R.id.isRootBadge);
        monMode = view.findViewById(R.id.isMonitModeOk);
        monBadge = view.findViewById(R.id.isMonitBadge);
        usbOtg = view.findViewById(R.id.isUsbOtgOk);
        usbBadge = view.findViewById(R.id.isUsbBadge);
        manufacture = view.findViewById(R.id.isManufactureOk);
        manufactureBadge = view.findViewById(R.id.isManufactureBadge);
        spaceSub = view.findViewById(R.id.isSpaceOk);
        spaceBadge = view.findViewById(R.id.isSpaceBadge);

        button.setOnClickListener(view12 -> {
            if (checked) {
                core.moveNext(mPager);
                ((AppIntroActivity) activity).getWaveDrawable().setLevel(6500);
                return;
            }
            runCheck();
        });

        if (core.getBoolean(PREF_CHECKED)) {
            restoreResults();
        }
        return view;
    }

    @SuppressLint("SetTextI18n")
    private void restoreResults() {
        boolean rootOk = core.getBoolean(PREF_ROOT);
        boolean monFinal = core.getBoolean(PREF_MON);
        boolean usbOk = core.getBoolean(PREF_USB);
        boolean manufactOk = core.getBoolean(PREF_MANUFACT);
        boolean spaceOk = core.getBoolean(PREF_SPACE);
        long freeGb = 0;
        try {
            freeGb = Long.parseLong(core.getString(PREF_FREE_GB));
        } catch (NumberFormatException ignored) {
        }

        progressIndicator.setVisibility(View.GONE);
        cardView.setVisibility(View.VISIBLE);
        capabilitiesLabel.setVisibility(View.VISIBLE);
        disclaimer.setVisibility(View.VISIBLE);

        renderRows(rootOk, monFinal, usbOk, manufactOk, spaceOk, freeGb);

        checked = true;
        button.setEnabled(true);
        button.setText(context.getResources().getString(R.string.next));
        button.setIconResource(R.drawable.bolt);
        ((AppIntroActivity) activity).getWaveDrawable().setLevel(5500);
    }

    @SuppressLint("SetTextI18n")
    private void renderRows(boolean rootOk, boolean monFinal, boolean usbOk,
                            boolean manufactOk, boolean spaceOk, long freeGb) {
        applyRow(rootOk, rootSub, rootBadge,
                "Superuser shell available",
                "su not detected — install Magisk or another root manager");
        applyRow(monFinal, monMode, monBadge,
                "Qualcomm + con_mode interface detected",
                "Monitor mode unlikely — capture/handshake tools may fail");
        applyRow(usbOk, usbOtg, usbBadge,
                "USB host mode supported",
                "External adapters won't be usable on this device");
        applyRow(manufactOk, manufacture, manufactureBadge,
                Build.MANUFACTURER + " — no known quirks",
                "Samsung stock ROM detected — wifi/local scan may misbehave");
        applyRow(spaceOk, spaceSub, spaceBadge,
                freeGb + " GB free on /data",
                "Only " + freeGb + " GB free — chroot install needs ~2 GB");
    }

    @SuppressLint("SetTextI18n")
    private void runCheck() {
        progressIndicator.setVisibility(View.VISIBLE);
        button.setEnabled(false);
        cancelled.set(false);

        new Thread(() -> {
            boolean rootOk = core.checkRoot();
            if (cancelled.get()) return;
            boolean usbOk = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);

            boolean monAvail = core.checkFile("/sys/module/wlan/parameters/con_mode");
            if (monAvail) {
                monAvail = Core.contains(core.customCommand(
                                "cat /proc/cpuinfo | grep \"Hardware\" | sed \"s/^Hardware.*: \\(.*\\)/\\1/g\""),
                        "Qualcomm");
            }
            boolean monFinal = monAvail;

            boolean manufactOk = !Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("samsung");

            long freeGb = freeStorageGb();
            boolean spaceOk = freeGb >= 2;

            core.putBoolean(PREF_ROOT, rootOk);
            core.putBoolean(PREF_MON, monFinal);
            core.putBoolean(PREF_USB, usbOk);
            core.putBoolean(PREF_MANUFACT, manufactOk);
            core.putBoolean(PREF_SPACE, spaceOk);
            core.putString(PREF_FREE_GB, String.valueOf(freeGb));
            core.putBoolean(PREF_CHECKED, true);

            if (cancelled.get() || activity == null || !isAdded()) return;

            activity.runOnUiThread(() -> {
                if (cancelled.get() || activity == null || !isAdded()) return;
                progressIndicator.setVisibility(View.GONE);
                cardView.setVisibility(View.VISIBLE);
                capabilitiesLabel.setVisibility(View.VISIBLE);
                disclaimer.setVisibility(View.VISIBLE);

                renderRows(rootOk, monFinal, usbOk, manufactOk, spaceOk, freeGb);

                checked = true;
                button.setEnabled(true);
                button.setText(context.getResources().getString(R.string.next));
                button.setIconResource(R.drawable.bolt);
                ((AppIntroActivity) activity).getWaveDrawable().setLevel(5500);
            });
        }).start();
    }

    private void applyRow(boolean ok, TextView subtitle, TextView badge,
                          String okText, String warnText) {
        subtitle.setText(ok ? okText : warnText);
        badge.setText(ok ? "OK" : "FAIL");
        int color = ContextCompat.getColor(context, ok ? R.color.green : R.color.red);
        badge.setTextColor(color);
        if (badge.getBackground() != null) {
            badge.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            badge.getBackground().setAlpha(40);
        }
    }

    @Override
    public void onDestroyView() {
        cancelled.set(true);
        super.onDestroyView();
    }

    @SuppressLint("UsableSpace")
    private long freeStorageGb() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytes = stat.getAvailableBytes();
            return bytes / (1024L * 1024L * 1024L);
        } catch (Throwable t) {
            return 0;
        }
    }
}
