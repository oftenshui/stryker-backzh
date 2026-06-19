package com.zalexdev.stryker.nmap;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.nmap.utils.ScanTarget;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NmapScanner extends Fragment {

    private static final String PREF_HISTORY = "nmap_target_history";
    private static final String PREF_PROFILE = "nmap_active_profile";
    private static final String PREF_CUSTOM_FLAGS = "nmap_custom_flags";
    private static final String PREF_LAST_OUTPUT = "nmap_last_output";
    private static final String PREF_LAST_TARGET = "nmap_last_target";
    private static final String PREF_LAST_LINES = "nmap_last_lines";
    private static final String PREF_LAST_PORTS = "nmap_last_ports";
    private static final String PREF_LAST_HOSTS = "nmap_last_hosts";
    private static final int MAX_HISTORY = 12;

    private TextInputLayout searchLayout;
    private TextInputEditText search;
    private MaterialButton startBtn;
    private MaterialButton stopBtn;
    private MaterialButton historyBtn;
    private MaterialButton copyBtn;
    private MaterialButton saveBtn;
    private MaterialButton shareBtn;
    private MaterialButton clearBtn;
    private MaterialButton expandBtn;
    private LinearProgressIndicator progressbar;
    private TextView output;
    private TextView subtitle;
    private TextView targetStatus;
    private TextView metricStatus;
    private TextView metricPorts;
    private TextView metricHosts;
    private TextView metricTime;
    private TextView metricLines;
    private ScrollView outputScroll;

    private Dialog fullscreenDialog;
    private TextView fullscreenOutput;
    private ScrollView fullscreenScroll;
    private TextView fullscreenMetric;

    private ChipGroup profileGroup;
    private Chip chipOs;
    private Chip chipServices;
    private Chip chipFast;
    private Chip chipPn;
    private Chip chipScripts;
    private Chip chipAggressive;
    private Chip chipUdp;
    private Chip chipVerbose;

    private Core core;
    private Context context;
    private Activity activity;

    private ScanTarget activeTask;
    private long scanStartedAt;
    private android.os.Handler elapsedHandler;
    private Runnable elapsedRunnable;
    private int linesCount;
    private int portsCount;
    private int hostsUp;
    private String activeProfile = "default";
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private final Pattern openPortPattern = Pattern.compile("^\\d+/(tcp|udp)\\s+open", Pattern.MULTILINE);
    private final Pattern hostUpPattern = Pattern.compile("Host is up");

    public NmapScanner() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nmap_fragment, container, false);
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        destroyed.set(false);

        searchLayout = view.findViewById(R.id.search_layout);
        search = view.findViewById(R.id.search);
        startBtn = view.findViewById(R.id.nmap_start_btn);
        stopBtn = view.findViewById(R.id.nmap_stop_btn);
        historyBtn = view.findViewById(R.id.nmap_history_btn);
        copyBtn = view.findViewById(R.id.nmap_copy_btn);
        saveBtn = view.findViewById(R.id.nmap_save_btn);
        shareBtn = view.findViewById(R.id.nmap_share_btn);
        clearBtn = view.findViewById(R.id.nmap_clear_btn);
        expandBtn = view.findViewById(R.id.nmap_expand_btn);
        progressbar = view.findViewById(R.id.progressbar);
        output = view.findViewById(R.id.nmap_output);
        outputScroll = view.findViewById(R.id.nmap_output_scroll);
        subtitle = view.findViewById(R.id.nmap_subtitle);
        targetStatus = view.findViewById(R.id.nmap_target_status);
        metricStatus = view.findViewById(R.id.nmap_metric_status);
        metricPorts = view.findViewById(R.id.nmap_metric_ports);
        metricHosts = view.findViewById(R.id.nmap_metric_hosts);
        metricTime = view.findViewById(R.id.nmap_metric_time);
        metricLines = view.findViewById(R.id.nmap_metric_lines);

        profileGroup = view.findViewById(R.id.nmap_profile_group);
        chipOs = view.findViewById(R.id.nmap_chip_os);
        chipServices = view.findViewById(R.id.nmap_chip_services);
        chipFast = view.findViewById(R.id.nmap_chip_fast);
        chipPn = view.findViewById(R.id.nmap_chip_pn);
        chipScripts = view.findViewById(R.id.nmap_chip_scripts);
        chipAggressive = view.findViewById(R.id.nmap_chip_aggressive);
        chipUdp = view.findViewById(R.id.nmap_chip_udp);
        chipVerbose = view.findViewById(R.id.nmap_chip_verbose);

        restoreProfile();
        wireProfileChips();
        applyProfile(activeProfile);

        startBtn.setOnClickListener(v -> startScan());
        stopBtn.setOnClickListener(v -> stopScan());
        historyBtn.setOnClickListener(v -> showHistory());
        copyBtn.setOnClickListener(v -> copyOutput());
        saveBtn.setOnClickListener(v -> saveOutput());
        shareBtn.setOnClickListener(v -> shareOutput());
        clearBtn.setOnClickListener(v -> clearOutput());
        expandBtn.setOnClickListener(v -> openFullscreen());

        restoreLastOutput();

        return view;
    }

    private void restoreLastOutput() {
        String lastTarget = core.getString(PREF_LAST_TARGET);
        if (lastTarget != null && !lastTarget.isEmpty()) {
            search.setText(lastTarget);
        }
        String lastOutput = core.getString(PREF_LAST_OUTPUT);
        linesCount = core.getInt(PREF_LAST_LINES, 0);
        portsCount = core.getInt(PREF_LAST_PORTS, 0);
        hostsUp = core.getInt(PREF_LAST_HOSTS, 0);
        if (lastOutput != null && !lastOutput.isEmpty()) {
            output.setText(lastOutput);
            if (!lastTarget.isEmpty()) {
                targetStatus.setText(lastTarget);
            }
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        }
        updateMetrics();
    }

    private void persistLastOutput() {
        if (core == null) return;
        core.putString(PREF_LAST_OUTPUT, output == null ? "" : output.getText().toString());
        core.putInt(PREF_LAST_LINES, linesCount);
        core.putInt(PREF_LAST_PORTS, portsCount);
        core.putInt(PREF_LAST_HOSTS, hostsUp);
        if (search != null) {
            String typed = search.getText() == null ? "" : search.getText().toString();
            core.putString(PREF_LAST_TARGET, typed);
        }
    }

    @Override
    public void onDestroyView() {
        destroyed.set(true);
        persistLastOutput();
        stopElapsedTicker();
        if (activeTask != null) {
            activeTask.kill();
            activeTask = null;
        }
        if (fullscreenDialog != null && fullscreenDialog.isShowing()) {
            fullscreenDialog.dismiss();
        }
        super.onDestroyView();
    }

    private void restoreProfile() {
        String saved = core.getString(PREF_PROFILE);
        if (saved != null && !saved.isEmpty()) {
            activeProfile = saved;
        }
    }

    private void wireProfileChips() {
        profileGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String profile;
            if (id == R.id.nmap_profile_fast) profile = "fast";
            else if (id == R.id.nmap_profile_full) profile = "full";
            else if (id == R.id.nmap_profile_intense) profile = "intense";
            else if (id == R.id.nmap_profile_custom) profile = "custom";
            else profile = "default";

            if ("custom".equals(profile)) {
                editCustomFlags();
            }
            activeProfile = profile;
            core.putString(PREF_PROFILE, profile);
            applyProfile(profile);
        });

        switch (activeProfile) {
            case "fast":
                ((Chip) profileGroup.findViewById(R.id.nmap_profile_fast)).setChecked(true);
                break;
            case "full":
                ((Chip) profileGroup.findViewById(R.id.nmap_profile_full)).setChecked(true);
                break;
            case "intense":
                ((Chip) profileGroup.findViewById(R.id.nmap_profile_intense)).setChecked(true);
                break;
            case "custom":
                ((Chip) profileGroup.findViewById(R.id.nmap_profile_custom)).setChecked(true);
                break;
            default:
                ((Chip) profileGroup.findViewById(R.id.nmap_profile_default)).setChecked(true);
                break;
        }
    }

    private void applyProfile(String profile) {
        switch (profile) {
            case "fast":
                chipOs.setChecked(false);
                chipServices.setChecked(false);
                chipFast.setChecked(true);
                chipPn.setChecked(false);
                chipScripts.setChecked(false);
                chipAggressive.setChecked(false);
                chipUdp.setChecked(false);
                chipVerbose.setChecked(false);
                break;
            case "full":
                chipOs.setChecked(false);
                chipServices.setChecked(true);
                chipFast.setChecked(false);
                chipPn.setChecked(false);
                chipScripts.setChecked(false);
                chipAggressive.setChecked(false);
                chipUdp.setChecked(false);
                chipVerbose.setChecked(true);
                break;
            case "intense":
                chipOs.setChecked(true);
                chipServices.setChecked(true);
                chipFast.setChecked(false);
                chipPn.setChecked(false);
                chipScripts.setChecked(true);
                chipAggressive.setChecked(true);
                chipUdp.setChecked(false);
                chipVerbose.setChecked(true);
                break;
            case "custom":
                break;
            default:
                chipOs.setChecked(false);
                chipServices.setChecked(true);
                chipFast.setChecked(false);
                chipPn.setChecked(false);
                chipScripts.setChecked(false);
                chipAggressive.setChecked(false);
                chipUdp.setChecked(false);
                chipVerbose.setChecked(false);
                break;
        }
    }

    private void editCustomFlags() {
        View dlg = LayoutInflater.from(context).inflate(R.layout.nmap_custom_dialog, null);
        TextInputEditText edit = dlg.findViewById(R.id.nmap_custom_input);
        edit.setText(core.getString(PREF_CUSTOM_FLAGS));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.nmap_custom_dialog_title)
                .setMessage(R.string.nmap_custom_dialog_body)
                .setView(dlg)
                .setPositiveButton(R.string.local_scan_target_apply, (d, w) -> {
                    String flags = edit.getText() == null ? "" : edit.getText().toString().trim();
                    core.putString(PREF_CUSTOM_FLAGS, flags);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showHistory() {
        ArrayList<String> history = loadHistory();
        if (history.isEmpty()) {
            toast(getString(R.string.nmap_recent_empty));
            return;
        }
        CharSequence[] items = history.toArray(new CharSequence[0]);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.nmap_target_recent)
                .setItems(items, (d, idx) -> search.setText(history.get(idx)))
                .setNegativeButton(R.string.nmap_recent_clear_all, (d, w) -> {
                    core.putString(PREF_HISTORY, "");
                    toast(getString(R.string.nmap_cleared));
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private ArrayList<String> loadHistory() {
        String raw = core.getString(PREF_HISTORY);
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split("␟")) {
            if (!part.isEmpty()) result.add(part);
        }
        return result;
    }

    private void rememberTarget(String target) {
        if (target == null || target.trim().isEmpty()) return;
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(target);
        ordered.addAll(loadHistory());
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String t : ordered) {
            if (i >= MAX_HISTORY) break;
            if (sb.length() > 0) sb.append('␟');
            sb.append(t);
            i++;
        }
        core.putString(PREF_HISTORY, sb.toString());
    }

    private void startScan() {
        String target = search.getText() == null ? "" : search.getText().toString().trim();
        if (target.isEmpty()) {
            searchLayout.setError(getString(R.string.nmap_target_invalid));
            return;
        }
        searchLayout.setError(null);
        rememberTarget(target);
        core.putString(PREF_LAST_TARGET, target);

        output.setText("");
        linesCount = 0;
        portsCount = 0;
        hostsUp = 0;
        updateMetrics();
        persistLastOutput();
        if (fullscreenOutput != null) {
            fullscreenOutput.setText("");
            fullscreenMetric.setText("0");
        }

        targetStatus.setText(target);
        subtitle.setText(R.string.nmap_subtitle_running);
        progressbar.setVisibility(View.VISIBLE);
        startBtn.setVisibility(View.GONE);
        stopBtn.setVisibility(View.VISIBLE);
        metricStatus.setText(R.string.nmap_status_running);
        metricStatus.setTextColor(Color.parseColor("#AB47BC"));
        scanStartedAt = System.currentTimeMillis();
        startElapsedTicker();

        String cmd = buildCommand(target);

        activeTask = new ScanTarget(cmd, context, activity, new ScanTarget.Callback() {
            @Override
            public void onLine(String line) {
                if (activity == null || !isAdded() || destroyed.get()) return;
                appendLine(line);
            }

            @Override
            public void onFinished(boolean ok) {
                if (activity == null || !isAdded() || destroyed.get()) return;
                activity.runOnUiThread(() -> {
                    if (activity == null || !isAdded() || destroyed.get()) return;
                    progressbar.setVisibility(View.INVISIBLE);
                    startBtn.setVisibility(View.VISIBLE);
                    stopBtn.setVisibility(View.GONE);
                    stopElapsedTicker();
                    if (ok) {
                        subtitle.setText(R.string.nmap_subtitle_done);
                        metricStatus.setText(R.string.nmap_status_done);
                        metricStatus.setTextColor(Color.parseColor("#388E3C"));
                    } else {
                        subtitle.setText(R.string.nmap_status_stopped);
                        metricStatus.setText(R.string.nmap_status_stopped);
                        metricStatus.setTextColor(Color.parseColor("#D32F2F"));
                    }
                    persistLastOutput();
                });
            }
        });
        activeTask.execute();
    }

    private void stopScan() {
        if (activeTask != null) {
            activeTask.kill();
        }
    }

    private String buildCommand(String target) {
        StringBuilder cmd = new StringBuilder("nmap ");
        if ("custom".equals(activeProfile)) {
            String flags = core.getString(PREF_CUSTOM_FLAGS);
            if (flags != null && !flags.isEmpty()) {
                cmd.append(flags).append(' ');
            }
        } else {
            if (chipOs.isChecked()) cmd.append("-O ");
            if (chipServices.isChecked()) cmd.append("-sV ");
            if (chipFast.isChecked()) cmd.append("-F --top-ports 100 ");
            if (chipPn.isChecked()) cmd.append("-Pn ");
            if (chipScripts.isChecked()) cmd.append("-sC ");
            if (chipAggressive.isChecked()) cmd.append("-A -T4 ");
            if (chipUdp.isChecked()) cmd.append("-sU ");
            if (chipVerbose.isChecked()) cmd.append("-v ");
            if ("full".equals(activeProfile)) cmd.append("-p- ");
        }
        cmd.append(target);
        return cmd.toString();
    }

    private void appendLine(String line) {
        if (activity == null || !isAdded() || destroyed.get()) return;
        activity.runOnUiThread(() -> {
            if (activity == null || !isAdded() || destroyed.get()) return;
            output.append(line + "\n");
            linesCount++;
            Matcher portMatch = openPortPattern.matcher(line);
            while (portMatch.find()) portsCount++;
            if (hostUpPattern.matcher(line).find()) hostsUp++;
            updateMetrics();
            persistLastOutput();
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
            if (fullscreenDialog != null && fullscreenDialog.isShowing() && fullscreenOutput != null) {
                fullscreenOutput.append(line + "\n");
                fullscreenMetric.setText(String.valueOf(linesCount));
                fullscreenScroll.post(() -> fullscreenScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void updateMetrics() {
        metricLines.setText(String.valueOf(linesCount));
        metricPorts.setText(String.valueOf(portsCount));
        metricHosts.setText(String.valueOf(hostsUp));
    }

    private void startElapsedTicker() {
        stopElapsedTicker();
        elapsedHandler = new android.os.Handler();
        elapsedRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - scanStartedAt) / 1000L;
                metricTime.setText(formatElapsed(elapsed));
                elapsedHandler.postDelayed(this, 1000L);
            }
        };
        elapsedHandler.post(elapsedRunnable);
    }

    private void stopElapsedTicker() {
        if (elapsedHandler != null && elapsedRunnable != null) {
            elapsedHandler.removeCallbacks(elapsedRunnable);
        }
    }

    private String formatElapsed(long sec) {
        if (sec < 60) return sec + "s";
        long m = sec / 60;
        long s = sec % 60;
        if (m < 60) return m + "m" + (s == 0 ? "" : s + "s");
        long h = m / 60;
        m = m % 60;
        return h + "h" + m + "m";
    }

    private void copyOutput() {
        String text = output.getText().toString();
        if (text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("nmap", text));
        toast(getString(R.string.nmap_copied));
    }

    private void clearOutput() {
        output.setText("");
        linesCount = 0;
        portsCount = 0;
        hostsUp = 0;
        updateMetrics();
        metricTime.setText("0s");
        metricStatus.setText(R.string.nmap_status_idle);
        metricStatus.setTextColor(Color.parseColor("#757575"));
        subtitle.setText(R.string.nmap_subtitle_idle);
        if (fullscreenOutput != null) {
            fullscreenOutput.setText("");
            fullscreenMetric.setText("0");
        }
        persistLastOutput();
        toast(getString(R.string.nmap_cleared));
    }

    private File writeOutputFile() {
        try {
            File dir = new File(core.getStorage() + "Stryker/nmap");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String name = "nmap-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
            File f = new File(dir, name);
            FileWriter fw = new FileWriter(f);
            fw.write(output.getText().toString());
            fw.close();
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveOutput() {
        if (output.getText().toString().trim().isEmpty()) return;
        File f = writeOutputFile();
        if (f != null) {
            toast(getString(R.string.nmap_saved_to, f.getAbsolutePath()));
        }
    }

    private void shareOutput() {
        if (output.getText().toString().trim().isEmpty()) return;
        File f = writeOutputFile();
        if (f == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            String target = search.getText() == null ? "" : search.getText().toString();
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nmap_share_subject, target));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.nmap_btn_share)));
        } catch (Exception e) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
            startActivity(Intent.createChooser(share, getString(R.string.nmap_btn_share)));
        }
    }

    private void openFullscreen() {
        fullscreenDialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fullscreenDialog.setContentView(R.layout.nmap_output_fullscreen);
        Window w = fullscreenDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0F0F0F")));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        fullscreenOutput = fullscreenDialog.findViewById(R.id.nmap_full_output);
        fullscreenScroll = fullscreenDialog.findViewById(R.id.nmap_full_scroll);
        fullscreenMetric = fullscreenDialog.findViewById(R.id.nmap_full_metric);
        MaterialButton close = fullscreenDialog.findViewById(R.id.nmap_full_close);
        fullscreenOutput.setText(output.getText());
        fullscreenMetric.setText(String.valueOf(linesCount));
        fullscreenScroll.post(() -> fullscreenScroll.fullScroll(View.FOCUS_DOWN));
        close.setOnClickListener(v -> fullscreenDialog.dismiss());
        fullscreenDialog.setOnDismissListener(d -> {
            fullscreenDialog = null;
            fullscreenOutput = null;
            fullscreenScroll = null;
            fullscreenMetric = null;
        });
        fullscreenDialog.show();
    }

    private void toast(String msg) {
        if (activity == null) return;
        activity.runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
}
