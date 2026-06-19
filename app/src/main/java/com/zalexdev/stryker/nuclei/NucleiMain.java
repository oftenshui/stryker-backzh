package com.zalexdev.stryker.nuclei;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Site;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NucleiMain extends Fragment implements TargetsAdapter.Listener {

    private Core core;
    private TargetsAdapter adapter;
    private RecyclerView recycler;
    private View emptyState;
    private TextView statTargets, statFindings, statRunning;
    private EditText searchField;
    private ImageView searchClear;
    private ChipGroup filterGroup;
    private ExtendedFloatingActionButton fab;

    private String searchTerm = "";
    private Filter activeFilter = Filter.ALL;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nuclei_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        core = new Core(getContext());

        recycler = view.findViewById(R.id.nuclei_targets);
        emptyState = view.findViewById(R.id.nuclei_empty);
        statTargets = view.findViewById(R.id.nuclei_stat_targets);
        statFindings = view.findViewById(R.id.nuclei_stat_findings);
        statRunning = view.findViewById(R.id.nuclei_stat_running);
        searchField = view.findViewById(R.id.nuclei_search);
        searchClear = view.findViewById(R.id.nuclei_search_clear);
        filterGroup = view.findViewById(R.id.nuclei_filters);
        fab = view.findViewById(R.id.nuclei_fab);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TargetsAdapter(getContext(), this);
        recycler.setAdapter(adapter);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchTerm = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                searchClear.setVisibility(searchTerm.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilters();
            }
        });
        searchClear.setOnClickListener(v -> searchField.setText(""));

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.nuclei_filter_running) activeFilter = Filter.RUNNING;
            else if (id == R.id.nuclei_filter_done) activeFilter = Filter.DONE;
            else if (id == R.id.nuclei_filter_failed) activeFilter = Filter.FAILED;
            else activeFilter = Filter.ALL;
            applyFilters();
        });

        fab.setOnClickListener(v -> showAddDialog());

        sweepStaleRunning();
        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(NucleiScanService.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(updateReceiver, filter);
        }
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void sweepStaleRunning() {
        new Thread(() -> {
            ArrayList<String> procs = core.customCommand("ps", false);
            ArrayList<Site> sites = core.getSites();
            boolean changed = false;
            for (int i = 0; i < sites.size(); i++) {
                Site s = sites.get(i);
                if ("Running".equals(s.status) && !Core.contains(procs, s.pid)) {
                    s.status = "Failed";
                    s.progress = "100";
                    core.changeSiteByPosition(s, i);
                    changed = true;
                }
            }
            if (changed && getActivity() != null) {
                getActivity().runOnUiThread(this::reload);
            }
        }, "nuclei-sweep").start();
    }

    private void reload() {
        if (getView() == null) return;
        ArrayList<Site> all = core.getSites();

        int totalTargets = all.size();
        int totalFindings = 0;
        int totalRunning = 0;
        for (Site s : all) {
            totalFindings += s.getNucleis() != null ? s.getNucleis().size() : 0;
            if ("Running".equals(s.status)) totalRunning++;
        }
        statTargets.setText(String.valueOf(totalTargets));
        statFindings.setText(String.valueOf(totalFindings));
        statRunning.setText(String.valueOf(totalRunning));

        applyFilters();
    }

    private void applyFilters() {
        ArrayList<Site> all = core.getSites();
        List<Site> visible = new ArrayList<>();
        List<Integer> indexMap = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Site s = all.get(i);
            if (!matchesFilter(s)) continue;
            if (!matchesSearch(s)) continue;
            visible.add(s);
            indexMap.add(i);
        }
        adapter.submit(visible, indexMap);
        boolean empty = visible.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean matchesFilter(Site s) {
        switch (activeFilter) {
            case RUNNING: return "Running".equals(s.status) || "Scheduled".equals(s.status);
            case DONE: return "Finished".equals(s.status);
            case FAILED: return "Failed".equals(s.status);
            case ALL:
            default: return true;
        }
    }

    private boolean matchesSearch(Site s) {
        if (searchTerm.isEmpty()) return true;
        return s.getUrl() != null && s.getUrl().toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    private void showAddDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_nuclei_add);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        TextInputEditText url = dialog.findViewById(R.id.nuclei_url);
        ChipGroup severityGroup = dialog.findViewById(R.id.nuclei_severity_group);
        MaterialButton start = dialog.findViewById(R.id.nuclei_start);
        MaterialButton cancel = dialog.findViewById(R.id.nuclei_cancel);

        cancel.setOnClickListener(v -> dialog.dismiss());
        start.setOnClickListener(v -> {
            String target = url.getText() == null ? "" : url.getText().toString().trim();
            if (target.length() < 4 || target.equals("https://")) {
                Toast.makeText(getContext(), "Enter a target URL", Toast.LENGTH_SHORT).show();
                return;
            }
            launchScan(target, minSeverityFromChips(severityGroup));
            dialog.dismiss();
        });
        dialog.show();
    }

    private String minSeverityFromChips(ChipGroup group) {
        int id = group.getCheckedChipId();
        if (id == R.id.nuclei_sev_low) return "low,medium,high,critical";
        if (id == R.id.nuclei_sev_medium) return "medium,high,critical";
        if (id == R.id.nuclei_sev_high) return "high,critical";
        return "info,low,medium,high,critical";
    }

    private void launchScan(String target, String minSeverity) {
        Site site = new Site();
        site.setUrl(target);
        site.setStatus("Scheduled");
        int newId = core.addSite(site);

        Intent intent = new Intent(getContext(), NucleiScanService.class);
        intent.putExtra(NucleiScanService.EXTRA_SITE_ID, newId);
        intent.putExtra(NucleiScanService.EXTRA_SEVERITY, minSeverity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
        reload();
    }

    @Override
    public void onOpen(int siteIndex, Site site) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContent, NucleiReport.forSite(siteIndex))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onStop(int siteIndex, Site site) {
        Intent intent = new Intent(getContext(), NucleiScanService.class);
        intent.setAction(NucleiScanService.ACTION_CANCEL);
        intent.putExtra(NucleiScanService.EXTRA_SITE_ID, siteIndex);
        requireContext().startService(intent);
    }

    @Override
    public void onRescan(int siteIndex, Site site) {
        site.status = "Scheduled";
        site.progress = "0";
        site.nucleis.clear();
        site.vulnsCount = new int[]{0, 0, 0, 0};
        core.changeSiteByPosition(site, siteIndex);

        Intent intent = new Intent(getContext(), NucleiScanService.class);
        intent.putExtra(NucleiScanService.EXTRA_SITE_ID, siteIndex);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
        reload();
    }

    @Override
    public void onMore(int siteIndex, Site site, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.nuclei_action_open);
        menu.getMenu().add(0, 6, 0, "Terminal log");
        menu.getMenu().add(0, 2, 0, R.string.nuclei_action_share);
        menu.getMenu().add(0, 3, 0, R.string.nuclei_action_copy);
        menu.getMenu().add(0, 4, 0, R.string.nuclei_action_rescan);
        menu.getMenu().add(0, 5, 0, R.string.nuclei_action_remove);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: onOpen(siteIndex, site); return true;
                case 2: shareReport(site); return true;
                case 3: copyUrl(site); return true;
                case 4: onRescan(siteIndex, site); return true;
                case 5: removeSite(siteIndex); return true;
                case 6: openTerminal(siteIndex); return true;
            }
            return false;
        });
        menu.show();
    }

    private void openTerminal(int siteIndex) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContent, NucleiTerminal.forSite(siteIndex))
                .addToBackStack(null)
                .commit();
    }

    private void shareReport(Site site) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Nuclei report: " + site.getUrl());
        StringBuilder body = new StringBuilder();
        body.append("Target: ").append(site.getUrl()).append('\n');
        body.append("Status: ").append(site.status).append('\n');
        body.append("Findings: ").append(site.getNucleis().size()).append('\n');
        body.append('\n');
        for (com.zalexdev.stryker.custom.NucleiItem item : site.getNucleis()) {
            body.append("• [").append(severityName(item.severity)).append("] ")
                    .append(item.title)
                    .append(" — ").append(item.host).append('\n');
        }
        share.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(share, "Share report"));
    }

    private void copyUrl(Site site) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Nuclei target", site.getUrl()));
        Toast.makeText(getContext(), "URL copied", Toast.LENGTH_SHORT).show();
    }

    private void removeSite(int siteIndex) {
        core.deleteSiteByPosition(siteIndex);
        reload();
    }

    static String severityName(int s) {
        switch (s) {
            case 1: return "LOW";
            case 2: return "MED";
            case 3: return "HIGH";
            case 4: return "CRIT";
            default: return "INFO";
        }
    }

    enum Filter { ALL, RUNNING, DONE, FAILED }
}
