package com.zalexdev.stryker.metasploit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.install.LogAdapter;
import com.zalexdev.stryker.appintro.install.LogLevel;
import com.zalexdev.stryker.appintro.install.LogLine;
import com.zalexdev.stryker.install.InstallService;
import com.zalexdev.stryker.metasploit.install.MsfInstallStage;
import com.zalexdev.stryker.utils.Core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InstallMetasploit extends Fragment {

    private static final String TOOL = InstallService.TOOL_METASPLOIT;

    public Activity activity;
    public Context context;
    public Core core;
    public FragmentManager fragmentManager;

    private MaterialButton actionBtn;
    private LinearProgressIndicator statusProgress;
    private ProgressBar statusSpinner;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;

    private TextView stagesHeader;
    private MaterialCardView stagesCard;
    private LinearLayout stagesContainer;

    private TextView logHeader;
    private RecyclerView logRecycler;
    private LogAdapter logAdapter;

    private final EnumMap<MsfInstallStage, StageRow> stageRows = new EnumMap<>(MsfInstallStage.class);
    private final LinkedHashMap<String, MsfInstallStage> stageMarkers = new LinkedHashMap<>();

    private boolean receiverRegistered = false;
    private final BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!TOOL.equals(i.getStringExtra(InstallService.EXTRA_TOOL))) return;
            String line = i.getStringExtra(InstallService.EXTRA_LINE);
            if (line != null) handleLine(line);
            String status = i.getStringExtra(InstallService.EXTRA_STATUS);
            if (status != null) onInstallStatus(status);
        }
    };

    public InstallMetasploit() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metasploit_install, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        fragmentManager = getParentFragmentManager();

        actionBtn = view.findViewById(R.id.install_metasploit);
        statusProgress = view.findViewById(R.id.msf_status_progress);
        statusSpinner = view.findViewById(R.id.msf_status_spinner);
        statusIcon = view.findViewById(R.id.msf_status_icon);
        statusTitle = view.findViewById(R.id.msf_status_title);
        statusSubtitle = view.findViewById(R.id.msf_status_subtitle);
        stagesHeader = view.findViewById(R.id.msf_install_stages_header);
        stagesCard = view.findViewById(R.id.msf_install_stages_card);
        stagesContainer = view.findViewById(R.id.msf_install_stages_container);
        logHeader = view.findViewById(R.id.msf_install_log_header);
        logRecycler = view.findViewById(R.id.msf_install_log_recycler);

        logRecycler.setLayoutManager(new LinearLayoutManager(context));
        logAdapter = new LogAdapter(context);
        logRecycler.setAdapter(logAdapter);

        buildStageRows(LayoutInflater.from(context));
        wireMarkers();

        actionBtn.setOnClickListener(v -> startInstall());

        if (core.getBoolean("msf")) {
            openTool();
            return;
        }

        registerInstallReceiver();

        String status = InstallService.reconcileStatus(context, core, TOOL);
        if (InstallService.STATUS_RUNNING.equals(status)
                || InstallService.STATUS_FAILED.equals(status)) {
            renderFromService(status);
        } else {
            new Thread(() -> {
                boolean msfcheck = core.checkFile("/data/local/stryker/release/metasploit-framework/msfconsole");
                runOnUi(() -> {
                    if (msfcheck) {
                        core.putBoolean("msf", true);
                        openTool();
                    } else {
                        showIdle();
                    }
                });
            }, "msf-install-check").start();
        }
    }

    @Override
    public void onDestroyView() {
        if (receiverRegistered) {
            try { requireContext().unregisterReceiver(installReceiver); } catch (IllegalArgumentException ignored) {}
            receiverRegistered = false;
        }
        super.onDestroyView();
    }

    private void registerInstallReceiver() {
        IntentFilter filter = new IntentFilter(InstallService.ACTION_UPDATED);
        ContextCompat.registerReceiver(requireContext(), installReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void wireMarkers() {
        stageMarkers.put("Updating packages", MsfInstallStage.REFRESH_INDEX);
        stageMarkers.put("Installing additional pkgs", MsfInstallStage.DEPS);
        stageMarkers.put("Downloading metasploit", MsfInstallStage.CLONE);
        stageMarkers.put("Pulling msfpc helper", MsfInstallStage.MSFPC);
        stageMarkers.put("Linking binaries", MsfInstallStage.LINK);
        stageMarkers.put("Installing msf pkgs and tools", MsfInstallStage.BUNDLER);
        stageMarkers.put("Initializing metasploit", MsfInstallStage.WARM);
        stageMarkers.put("Making sure everything is ready", MsfInstallStage.VERIFY);
    }

    @SuppressLint("SetTextI18n")
    private void startInstall() {
        showRunningChrome();
        resetStages();
        logAdapter.clear();
        InstallService.start(context, TOOL);
    }

    private void renderFromService(String status) {
        logAdapter.clear();
        resetStages();
        List<String> log = InstallService.readLog(context, TOOL);
        for (String line : log) handleLine(line);
        onInstallStatus(status);
    }

    private void onInstallStatus(String status) {
        if (InstallService.STATUS_DONE.equals(status)) showDone();
        else if (InstallService.STATUS_FAILED.equals(status)) showFailed();
        else if (InstallService.STATUS_RUNNING.equals(status)) showRunningChrome();
    }

    @SuppressLint("SetTextI18n")
    private void showIdle() {
        actionBtn.setText(R.string.msf_install_btn_start);
        actionBtn.setOnClickListener(v -> startInstall());
        actionBtn.setVisibility(View.VISIBLE);
        statusSpinner.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusProgress.setVisibility(View.GONE);
        statusSubtitle.setText(R.string.msf_install_card_status_idle);
    }

    private void showRunningChrome() {
        actionBtn.setVisibility(View.GONE);
        stagesHeader.setVisibility(View.VISIBLE);
        stagesCard.setVisibility(View.VISIBLE);
        logHeader.setVisibility(View.VISIBLE);
        logRecycler.setVisibility(View.VISIBLE);
        statusProgress.setVisibility(View.VISIBLE);
        statusSpinner.setVisibility(View.VISIBLE);
        statusIcon.setVisibility(View.GONE);
        statusSubtitle.setText(R.string.msf_install_card_status_running);
    }

    private void showDone() {
        core.putBoolean("msf", true);
        statusSpinner.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setImageResource(R.drawable.done);
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.green), PorterDuff.Mode.SRC_IN);
        statusProgress.setVisibility(View.GONE);
        statusSubtitle.setText(R.string.msf_install_card_status_done);
        actionBtn.setText(R.string.msf_install_btn_open);
        actionBtn.setIconResource(R.drawable.shield);
        actionBtn.setVisibility(View.VISIBLE);
        actionBtn.setOnClickListener(v -> openTool());
    }

    private void showFailed() {
        statusSpinner.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setImageResource(R.drawable.error);
        statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.red), PorterDuff.Mode.SRC_IN);
        statusProgress.setVisibility(View.GONE);
        statusSubtitle.setText(R.string.msf_install_card_status_failed);
        actionBtn.setText(R.string.msf_install_btn_retry);
        actionBtn.setIconResource(R.drawable.repeat);
        actionBtn.setOnClickListener(v -> startInstall());
        actionBtn.setVisibility(View.VISIBLE);
    }

    private void openTool() {
        if (!isAdded()) return;
        fragmentManager.beginTransaction().replace(R.id.flContent, new Metasploit()).commit();
    }

    private void handleLine(String line) {
        if (line == null) return;
        if (line.contains("×")) {
            String marker = line.replace("×", "").trim();
            for (Map.Entry<MsfInstallStage, StageRow> e : stageRows.entrySet()) {
                if (e.getValue().state == RowState.ACTIVE) markStage(e.getKey(), RowState.DONE);
            }
            for (Map.Entry<String, MsfInstallStage> e : stageMarkers.entrySet()) {
                if (marker.startsWith(e.getKey())) {
                    markStage(e.getValue(), RowState.ACTIVE);
                    break;
                }
            }
            runOnUi(() -> statusSubtitle.setText(marker));
            appendLog(LogLevel.STEP, marker);
            return;
        }
        if (line.contains("OK:")) appendLog(LogLevel.SUCCESS, line);
        else if (line.contains("ERROR") || line.contains("Errno") || line.contains("fatal:")) {
            appendLog(LogLevel.ERROR, line);
        } else if (line.contains("Cloning") || line.contains("Receiving")) {
            if (stageRows.get(MsfInstallStage.CLONE) != null
                    && stageRows.get(MsfInstallStage.CLONE).state == RowState.PENDING) {
                markStage(MsfInstallStage.CLONE, RowState.ACTIVE);
            }
            appendLog(LogLevel.CMD, line);
        } else if (line.contains("Fetching") || line.contains("Installing")) {
            appendLog(LogLevel.INFO, line);
        } else if (line.contains("Bundle complete")) {
            markStage(MsfInstallStage.BUNDLE, RowState.DONE);
            appendLog(LogLevel.SUCCESS, line);
        } else if (line.contains("metasploit v")) {
            markStage(MsfInstallStage.WARM, RowState.DONE);
            markStage(MsfInstallStage.VERIFY, RowState.DONE);
            appendLog(LogLevel.SUCCESS, line);
        } else if (line.length() > 0) {
            appendLog(LogLevel.INFO, line);
        }
    }

    private enum RowState { PENDING, ACTIVE, DONE, FAILED }

    private void buildStageRows(LayoutInflater inflater) {
        stagesContainer.removeAllViews();
        stageRows.clear();
        for (MsfInstallStage stage : MsfInstallStage.values()) {
            View row = inflater.inflate(R.layout.install_stage_row, stagesContainer, false);
            TextView title = row.findViewById(R.id.stage_title);
            ImageView icon = row.findViewById(R.id.stage_icon);
            ProgressBar spinner = row.findViewById(R.id.stage_spinner);
            FrameLayout indicator = row.findViewById(R.id.stage_indicator);
            title.setText(stage.titleRes);
            StageRow handles = new StageRow(title, icon, spinner, indicator);
            applyRowState(handles, RowState.PENDING);
            stageRows.put(stage, handles);
            stagesContainer.addView(row);
        }
    }

    private void resetStages() {
        runOnUi(() -> {
            for (StageRow row : stageRows.values()) applyRowState(row, RowState.PENDING);
        });
    }

    private void markStage(MsfInstallStage stage, RowState newState) {
        runOnUi(() -> {
            StageRow row = stageRows.get(stage);
            if (row == null) return;
            applyRowState(row, newState);
        });
    }

    private void applyRowState(StageRow row, RowState state) {
        row.state = state;
        int color;
        switch (state) {
            case ACTIVE:
                color = ContextCompat.getColor(context, R.color.stryker_accent);
                row.spinner.setVisibility(View.VISIBLE);
                row.icon.setVisibility(View.GONE);
                row.title.setTypeface(null, Typeface.BOLD);
                break;
            case DONE:
                color = ContextCompat.getColor(context, R.color.green);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.VISIBLE);
                row.icon.setImageResource(R.drawable.done);
                row.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                row.title.setTypeface(null, Typeface.NORMAL);
                break;
            case FAILED:
                color = ContextCompat.getColor(context, R.color.red);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.VISIBLE);
                row.icon.setImageResource(R.drawable.error);
                row.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                row.title.setTypeface(null, Typeface.BOLD);
                break;
            case PENDING:
            default:
                color = ContextCompat.getColor(context, R.color.grey);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.GONE);
                row.title.setTypeface(null, Typeface.NORMAL);
                break;
        }
        row.title.setTextColor(color);
        if (row.indicator.getBackground() != null) {
            row.indicator.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            row.indicator.getBackground().setAlpha(60);
        }
    }

    private void appendLog(LogLevel level, String text) {
        runOnUi(() -> {
            logAdapter.append(new LogLine(level, text));
            logRecycler.scrollToPosition(logAdapter.size() - 1);
        });
    }

    private void runOnUi(Runnable r) {
        if (activity != null && isAdded()) activity.runOnUiThread(r);
    }

    private static final class StageRow {
        final TextView title;
        final ImageView icon;
        final ProgressBar spinner;
        final FrameLayout indicator;
        RowState state = RowState.PENDING;

        StageRow(TextView title, ImageView icon, ProgressBar spinner, FrameLayout indicator) {
            this.title = title;
            this.icon = icon;
            this.spinner = spinner;
            this.indicator = indicator;
        }
    }
}
