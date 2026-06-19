package com.zalexdev.stryker.metasploit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.MsfExploit;
import com.zalexdev.stryker.metasploit.adapters.ExploitAdapter;
import com.zalexdev.stryker.metasploit.utils.MetasploitUtils;
import com.zalexdev.stryker.metasploit.utils.MsfRpcConsole;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Metasploit extends Fragment {

    private static final String PREF_LAST_SEARCH = "msf_last_search";
    private static final String PREF_FILTER_CHIP = "msf_filter_chip";

    public Activity activity;
    public Context context;
    public Core core;
    public MetasploitUtils metasploitUtils;
    public ExploitAdapter adapter;
    public String ip;
    public ArrayList<String> ports;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private TextView subtitle;
    private TextView initTitle;
    private TextView initSubtitle;
    private TextView stateChip;
    private TextView exploitsCount;
    private TextView scannersCount;
    private TextView sessionsCount;
    private LinearProgressIndicator bootProgress;
    private ShimmerFrameLayout shimmerExp;
    private ShimmerFrameLayout shimmerAux;
    private ShimmerFrameLayout shimmerSes;
    private ShimmerFrameLayout logoShimmer;
    private TextInputEditText search;
    private RecyclerView exploitsList;
    private MaterialCardView listCard;
    private MaterialCardView emptyCard;
    private ChipGroup filterGroup;
    private View actionRestart;
    private View actionPayload;
    private com.facebook.shimmer.ShimmerFrameLayout skeletonOverlay;
    private ImageView msfImg;
    private SwipeRefreshLayout refresh;

    private MetasploitUtils.StateListener stateListener;

    public Metasploit() {
    }

    public Metasploit(String ip, ArrayList<String> ports) {
        this.ip = ip;
        this.ports = ports;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metasploit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        MainActivity main = (MainActivity) activity;

        subtitle = view.findViewById(R.id.msf_subtitle);
        initTitle = view.findViewById(R.id.msf_init);
        initSubtitle = view.findViewById(R.id.msf_init_subtitle);
        stateChip = view.findViewById(R.id.msf_state_chip);
        exploitsCount = view.findViewById(R.id.msf_exploit_counter);
        scannersCount = view.findViewById(R.id.scanners_count);
        sessionsCount = view.findViewById(R.id.msf_sessions_counter);
        bootProgress = view.findViewById(R.id.msf_boot_progress);
        shimmerExp = view.findViewById(R.id.shimmerexpl);
        shimmerAux = view.findViewById(R.id.shimmeraux);
        shimmerSes = view.findViewById(R.id.shimmerses);
        logoShimmer = view.findViewById(R.id.msf_logo_shimmer);
        search = view.findViewById(R.id.getsearch);
        exploitsList = view.findViewById(R.id.exploits_list);
        listCard = view.findViewById(R.id.msf_list_card);
        emptyCard = view.findViewById(R.id.msf_empty_card);
        filterGroup = view.findViewById(R.id.msf_filter_group);
        actionRestart = view.findViewById(R.id.msf_action_restart);
        actionPayload = view.findViewById(R.id.msf_action_payload);
        msfImg = view.findViewById(R.id.msf_img);
        refresh = view.findViewById(R.id.msf_refresh);
        skeletonOverlay = view.findViewById(R.id.msf_skeleton_overlay);
        skeletonOverlay.showShimmer(true);

        exploitsList.setLayoutManager(new LinearLayoutManager(context));
        exploitsList.setHasFixedSize(true);
        exploitsList.setItemViewCacheSize(16);
        exploitsList.setItemAnimator(null);

        startShimmers(true);

        if (!core.getBoolean("msf")) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.flContent, new InstallMetasploit()).commit();
            return;
        }

        refresh.setOnRefreshListener(() -> {
            if (skeletonOverlay != null) {
                skeletonOverlay.setVisibility(View.VISIBLE);
                skeletonOverlay.showShimmer(true);
            }
            new Thread(this::reloadModules, "msf-reload-pull").start();
        });

        new Thread(() -> {
            boolean msfcheck = core.checkFile("/data/local/stryker/release/metasploit-framework/msfconsole");
            if (!isAdded() || activity == null) return;
            if (!msfcheck) {
                core.putBoolean("msf", false);
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.flContent, new InstallMetasploit()).commit();
                });
                return;
            }
            metasploitUtils = main.getMetasploitUtils();
            if (metasploitUtils == null) {
                MetasploitUtils fresh = new MetasploitUtils(context, activity);
                main.setMetasploitUtils(fresh);
                metasploitUtils = fresh;
            }
            if (!isAdded() || activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                wireStateListener();
                wireActions();
                wireSearchAndFilters();
                renderConsoleState(metasploitUtils.console.getState(), null);
            });
            if (metasploitUtils.isInitializedConsole && metasploitUtils.isAliveConsole()) {
                reloadModules();
            } else if (!metasploitUtils.isAliveConsole()) {
                metasploitUtils.initConsole();
            }
        }, "msf-hub-bootstrap").start();
    }

    private void wireStateListener() {
        stateListener = new MetasploitUtils.StateListener() {
            @Override public void onConsoleState(MsfRpcConsole.State state, String reason) {
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (activity != null && isAdded()) renderConsoleState(state, reason);
                    });
                }
                if (state == MsfRpcConsole.State.READY) {
                    new Thread(Metasploit.this::reloadModules, "msf-reload").start();
                }
            }
            @Override public void onShellState(MsfRpcConsole.State state, String reason) {
            }
        };
        metasploitUtils.addStateListener(stateListener);
    }

    private void wireActions() {
        actionRestart.setOnClickListener(v -> {
            toast(R.string.msf_console_restart_toast);
            startShimmers(true);
            new Thread(() -> {
                boolean ok = metasploitUtils.restartConsole();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (activity != null && isAdded())
                            toast(ok ? R.string.msf_console_ready_toast : R.string.msf_console_failed_toast);
                    });
                }
            }, "msf-restart-console").start();
        });
        actionPayload.setOnClickListener(v -> com.zalexdev.stryker.metasploit.PayloadGenerator
                .show(context, activity, core));
    }

    private void wireSearchAndFilters() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (core != null) core.putString(PREF_LAST_SEARCH, s.toString());
                if (adapter != null) {
                    adapter.search(s.toString());
                    updateEmpty();
                }
            }
        });
        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (core != null) core.putInt(PREF_FILTER_CHIP, id);
            if (adapter == null) return;
            if (id == R.id.msf_filter_exploits)      adapter.setFilter(ExploitAdapter.Filter.EXPLOITS);
            else if (id == R.id.msf_filter_aux)       adapter.setFilter(ExploitAdapter.Filter.AUX);
            else                                       adapter.setFilter(ExploitAdapter.Filter.ALL);
            updateEmpty();
        });
        String savedSearch = core.getString(PREF_LAST_SEARCH);
        if (savedSearch != null && !savedSearch.isEmpty()
                && (search.getText() == null || search.getText().length() == 0)) {
            search.setText(savedSearch);
        }
        int savedChip = core.getInt(PREF_FILTER_CHIP, 0);
        if (savedChip != 0 && filterGroup.getCheckedChipId() != savedChip
                && filterGroup.findViewById(savedChip) != null) {
            filterGroup.check(savedChip);
        }
    }

    private void renderConsoleState(MsfRpcConsole.State state, String reason) {
        switch (state) {
            case READY:
                stateChip.setText(R.string.msf_status_pill_online);
                stateChip.setTextColor(Color.parseColor("#388E3C"));
                bootProgress.setVisibility(View.INVISIBLE);
                subtitle.setText(R.string.msf_subtitle_ready);
                initSubtitle.setText(getString(R.string.msf_version_label, metasploitUtils.version));
                if (logoShimmer != null) logoShimmer.hideShimmer();
                break;
            case BOOTING:
                stateChip.setText(R.string.msf_status_pill_loading);
                stateChip.setTextColor(Color.parseColor("#AB47BC"));
                bootProgress.setVisibility(View.VISIBLE);
                subtitle.setText(R.string.msf_subtitle_loading);
                initSubtitle.setText(R.string.msf_subtitle_loading);
                if (logoShimmer != null) logoShimmer.showShimmer(true);
                break;
            case DEAD:
            case IDLE:
            default:
                stateChip.setText(R.string.msf_status_pill_offline);
                stateChip.setTextColor(Color.parseColor("#E53935"));
                bootProgress.setVisibility(View.INVISIBLE);
                subtitle.setText(R.string.msf_subtitle_offline);
                initSubtitle.setText(reason == null ? getString(R.string.msf_subtitle_offline) : reason);
                if (logoShimmer != null) logoShimmer.hideShimmer();
                break;
        }
    }

    private void reloadModules() {
        ArrayList<MsfExploit> exploits = metasploitUtils.getExploits();
        ArrayList<MsfExploit> aux = metasploitUtils.getAuxiliary();
        exploits.addAll(aux);

        if (cancelled.get() || activity == null || !isAdded()) return;
        activity.runOnUiThread(() -> {
            if (cancelled.get() || activity == null || !isAdded()) return;
            exploitsCount.setText(String.valueOf(exploits.size() - aux.size()));
            scannersCount.setText(String.valueOf(aux.size()));
            sessionsCount.setText("0");
            startShimmers(false);
            adapter = new ExploitAdapter(context, activity, exploits);
            exploitsList.setAdapter(adapter);
            if (skeletonOverlay != null) {
                skeletonOverlay.hideShimmer();
                skeletonOverlay.setVisibility(View.GONE);
            }
            if (refresh != null) refresh.setRefreshing(false);
            if (metasploitUtils.isInitializedConsole) {
                renderConsoleState(MsfRpcConsole.State.READY, null);
            }
            int savedChip = core.getInt(PREF_FILTER_CHIP, 0);
            if (savedChip == R.id.msf_filter_exploits)  adapter.setFilter(ExploitAdapter.Filter.EXPLOITS);
            else if (savedChip == R.id.msf_filter_aux)  adapter.setFilter(ExploitAdapter.Filter.AUX);
            else                                        adapter.setFilter(ExploitAdapter.Filter.ALL);
            if (search.getText() != null) adapter.search(search.getText().toString());
            updateEmpty();
        });
    }

    private void updateEmpty() {
        if (adapter == null) return;
        boolean empty = adapter.getItemCount() == 0;
        listCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyCard.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void startShimmers(boolean on) {
        if (on) {
            shimmerExp.showShimmer(true);
            shimmerAux.showShimmer(true);
            shimmerSes.showShimmer(true);
        } else {
            shimmerExp.hideShimmer();
            shimmerAux.hideShimmer();
            shimmerSes.hideShimmer();
        }
    }

    private void toast(int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        cancelled.set(true);
        super.onDestroyView();
        if (metasploitUtils != null && stateListener != null) {
            metasploitUtils.removeStateListener(stateListener);
        }
    }
}
