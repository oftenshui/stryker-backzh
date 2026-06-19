package com.zalexdev.stryker.nuclei;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.NucleiItem;
import com.zalexdev.stryker.custom.Site;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NucleiReport extends Fragment {

    private static final String ARG_INDEX = "siteIndex";

    private int siteIndex = -1;
    private Site site;
    private Core core;

    private TextView url;
    private TextView subtitle;
    private TextView stateChip;
    private LinearProgressIndicator progress;
    private MaterialButton terminalBtn;
    private RecyclerView findings;
    private FindingsAdapter adapter;
    private View empty;
    private TextView emptyTitle, emptySubtitle;
    private View emptyIconWrap;
    private ImageView emptyIcon;
    private ProgressBar emptySpinner;
    private EditText searchField;
    private ImageView searchClear;
    private ChipGroup filterGroup;

    private String searchTerm = "";
    private int severityFilter = -1;

    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int changed = intent.getIntExtra(NucleiScanService.EXTRA_SITE_ID, -1);
            if (changed == siteIndex) reload();
        }
    };

    public static NucleiReport forSite(int siteIndex) {
        NucleiReport r = new NucleiReport();
        Bundle b = new Bundle();
        b.putInt(ARG_INDEX, siteIndex);
        r.setArguments(b);
        return r;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nuclei_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        core = new Core(getContext());
        if (getArguments() != null) siteIndex = getArguments().getInt(ARG_INDEX, -1);

        url = view.findViewById(R.id.report_url);
        subtitle = view.findViewById(R.id.report_subtitle);
        stateChip = view.findViewById(R.id.report_state_chip);
        progress = view.findViewById(R.id.report_progress);
        terminalBtn = view.findViewById(R.id.report_terminal);
        findings = view.findViewById(R.id.report_findings);
        empty = view.findViewById(R.id.report_empty);
        emptyTitle = view.findViewById(R.id.report_empty_title);
        emptySubtitle = view.findViewById(R.id.report_empty_subtitle);
        emptyIconWrap = view.findViewById(R.id.report_empty_icon_wrap);
        emptyIcon = view.findViewById(R.id.report_empty_icon);
        emptySpinner = view.findViewById(R.id.report_empty_spinner);

        MaterialButton back = view.findViewById(R.id.report_back);
        back.setRotation(180f);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        MaterialButton share = view.findViewById(R.id.report_share);
        share.setOnClickListener(v -> shareReport());

        terminalBtn.setOnClickListener(v -> openTerminal());

        searchField = view.findViewById(R.id.report_search);
        searchClear = view.findViewById(R.id.report_search_clear);
        filterGroup = view.findViewById(R.id.report_filters);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchTerm = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                searchClear.setVisibility(searchTerm.isEmpty() ? View.GONE : View.VISIBLE);
                rebindFindings();
            }
        });
        searchClear.setOnClickListener(v -> searchField.setText(""));

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                severityFilter = -1;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.report_filter_critical) severityFilter = NucleiItem.CRITICAL;
                else if (id == R.id.report_filter_high) severityFilter = NucleiItem.HIGH;
                else if (id == R.id.report_filter_medium) severityFilter = NucleiItem.MEDIUM;
                else if (id == R.id.report_filter_low) severityFilter = NucleiItem.LOW;
                else if (id == R.id.report_filter_info) severityFilter = NucleiItem.INFO;
                else severityFilter = -1;
            }
            rebindFindings();
        });

        findings.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FindingsAdapter(new ArrayList<>());
        findings.setAdapter(adapter);

        if (savedInstanceState != null) {
            String s = savedInstanceState.getString("nr_search", "");
            if (s != null && !s.isEmpty()) searchField.setText(s);
        }

        reload();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("nr_search", searchTerm);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(NucleiScanService.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updates, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(updates, f);
        }
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updates);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void reload() {
        if (getView() == null) return;
        ArrayList<Site> all = core.getSites();
        if (siteIndex < 0 || siteIndex >= all.size()) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        site = all.get(siteIndex);

        url.setText(site.getUrl());
        TargetsAdapter.State state = TargetsAdapter.State.from(site.status);
        int pct = TargetsAdapter.parseProgress(site.progress);
        bindStatus(state, pct);
        bindSeverityPills(getView());

        rebindFindings();
    }

    private void rebindFindings() {
        if (site == null) return;
        List<NucleiItem> visible = new ArrayList<>();
        for (NucleiItem item : site.getNucleis()) {
            if (severityFilter != -1 && item.severity != severityFilter) continue;
            if (!matchesSearch(item)) continue;
            visible.add(item);
        }
        adapter = new FindingsAdapter(visible);
        findings.setAdapter(adapter);

        boolean nothingToShow = visible.isEmpty();
        empty.setVisibility(nothingToShow ? View.VISIBLE : View.GONE);
        findings.setVisibility(nothingToShow ? View.GONE : View.VISIBLE);
        if (nothingToShow) {
            TargetsAdapter.State state = TargetsAdapter.State.from(site.status);
            int pct = TargetsAdapter.parseProgress(site.progress);
            if (site.getNucleis().isEmpty()) {
                bindEmptyState(state, pct);
            } else {
                bindNoMatchState();
            }
        }
    }

    private boolean matchesSearch(NucleiItem item) {
        if (searchTerm.isEmpty()) return true;
        if (item.title != null && item.title.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
        if (item.host != null && item.host.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
        if (item.description != null && item.description.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
        if (item.cve != null && item.cve.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
        if (item.results != null && item.results.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
        if (item.tags != null) {
            for (String t : item.tags) {
                if (t != null && t.toLowerCase(Locale.ROOT).contains(searchTerm)) return true;
            }
        }
        return false;
    }

    private void bindNoMatchState() {
        emptyIcon.setVisibility(View.VISIBLE);
        emptySpinner.setVisibility(View.GONE);
        emptyIconWrap.setBackgroundResource(R.drawable.dashboard_tile_bg_indigo);
        emptyIcon.setImageResource(R.drawable.search);
        emptyIcon.setColorFilter(0xFF3949AB, PorterDuff.Mode.SRC_IN);
        emptyTitle.setText("No matches");
        emptySubtitle.setText("No findings match the current search/filter.");
    }

    private void bindStatus(TargetsAdapter.State state, int pct) {
        int color;
        String chip;
        String subtitleText = site.getNucleis().size() + " findings · ";
        switch (state) {
            case RUNNING:
                color = ContextCompat.getColor(requireContext(), R.color.stryker_accent);
                chip = "RUN";
                subtitleText += "Running · " + pct + "%";
                progress.setVisibility(View.VISIBLE);
                if (pct > 0) {
                    progress.setIndeterminate(false);
                    progress.setProgressCompat(pct, true);
                } else {
                    progress.setIndeterminate(true);
                }
                break;
            case FINISHED:
                color = ContextCompat.getColor(requireContext(), R.color.green);
                chip = "DONE";
                subtitleText += "Finished";
                progress.setVisibility(View.GONE);
                break;
            case FAILED:
                color = ContextCompat.getColor(requireContext(), R.color.red);
                chip = "FAIL";
                subtitleText += "Failed";
                progress.setVisibility(View.GONE);
                break;
            case SCHEDULED:
            default:
                color = ContextCompat.getColor(requireContext(), R.color.grey);
                chip = "QUEUE";
                subtitleText += "Queued";
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(true);
                break;
        }
        subtitle.setText(subtitleText);
        stateChip.setText(chip);
        stateChip.setTextColor(color);
        if (stateChip.getBackground() != null) {
            stateChip.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            stateChip.getBackground().setAlpha(40);
        }
    }

    private void bindEmptyState(TargetsAdapter.State state, int pct) {
        switch (state) {
            case RUNNING:
            case SCHEDULED:
                emptyIcon.setVisibility(View.GONE);
                emptySpinner.setVisibility(View.VISIBLE);
                emptyIconWrap.setBackgroundResource(R.drawable.dashboard_tile_bg_indigo);
                emptyTitle.setText("Scanning…");
                emptySubtitle.setText("Findings will stream in as nuclei templates match — keep this view open.");
                break;
            case FAILED:
                emptyIcon.setVisibility(View.VISIBLE);
                emptySpinner.setVisibility(View.GONE);
                emptyIconWrap.setBackgroundResource(R.drawable.dashboard_tile_bg_red);
                emptyIcon.setImageResource(R.drawable.error);
                emptyIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red),
                        PorterDuff.Mode.SRC_IN);
                emptyTitle.setText("Scan failed");
                emptySubtitle.setText("Open the terminal log to see what nuclei said.");
                break;
            case FINISHED:
            default:
                emptyIcon.setVisibility(View.VISIBLE);
                emptySpinner.setVisibility(View.GONE);
                emptyIconWrap.setBackgroundResource(R.drawable.dashboard_tile_bg_green);
                emptyIcon.setImageResource(R.drawable.done);
                emptyIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green),
                        PorterDuff.Mode.SRC_IN);
                emptyTitle.setText("No findings");
                emptySubtitle.setText("Either the target is clean or no template matched.");
                break;
        }
    }

    private void bindSeverityPills(View view) {
        int[] counts = site.vulnsCount != null && site.vulnsCount.length >= 4
                ? site.vulnsCount : new int[]{0, 0, 0, 0};
        applyPill(view.findViewById(R.id.report_sev_critical), "C", counts.length > 4 ? counts[4] : 0, 0xFFB71C1C);
        applyPill(view.findViewById(R.id.report_sev_high), "H", counts[3], 0xFFD32F2F);
        applyPill(view.findViewById(R.id.report_sev_medium), "M", counts[2], 0xFFEF6C00);
        applyPill(view.findViewById(R.id.report_sev_low), "L", counts[1], 0xFFF9A825);
        applyPill(view.findViewById(R.id.report_sev_info), "I", counts[0], 0xFF1565C0);
    }

    private void applyPill(View root, String letter, int count, int color) {
        TextView label = root.findViewById(R.id.sev_label);
        View dot = root.findViewById(R.id.sev_dot);
        label.setText(letter + " " + count);
        if (count == 0) {
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey));
            dot.getBackground().setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.grey), PorterDuff.Mode.SRC_IN);
        } else {
            label.setTextColor(color);
            dot.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    private void openTerminal() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContent, NucleiTerminal.forSite(siteIndex))
                .addToBackStack(null)
                .commit();
    }

    private void shareReport() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Nuclei report: " + site.getUrl());
        StringBuilder body = new StringBuilder();
        body.append("Target: ").append(site.getUrl()).append('\n');
        body.append("Status: ").append(site.status).append('\n');
        body.append("Findings: ").append(site.getNucleis().size()).append("\n\n");
        for (com.zalexdev.stryker.custom.NucleiItem item : site.getNucleis()) {
            body.append("• [").append(NucleiMain.severityName(item.severity)).append("] ")
                    .append(item.title)
                    .append(" — ").append(item.host).append('\n');
        }
        share.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(share, "Share report"));
    }
}
