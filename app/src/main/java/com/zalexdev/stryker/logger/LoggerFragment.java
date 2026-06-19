package com.zalexdev.stryker.logger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.settings.SettingsNew;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class LoggerFragment extends Fragment {

    private static final int PAGE = 200;
    private static final int LIVE_CAP = 500;
    private static final int PREFETCH = 15;
    private static final int MAX_VIEW_ROWS = 3_000;
    private static final long SEARCH_DEBOUNCE_MS = 300L;
    private static final long LIVE_DEBOUNCE_MS = 250L;
    private static final long COUNT_RESYNC_MS = 1500L;

    @SuppressLint("SdCardPath")
    private static final String EXPORT_SDCARD = "/sdcard/Stryker/stryker.log";

    private Activity activity;
    private Context context;
    private Core core;
    private LogStore store;

    private final LogFilter filter = new LogFilter();
    private LoggerAdapter adapter;
    private LinearLayoutManager layoutManager;
    private RecyclerView recyclerView;
    private LinearProgressIndicator progress;
    private View emptyState;
    private MaterialTextView subtitle;
    private EditText search;
    private View searchClear;
    private ChipGroup levelChips;
    private ChipGroup toolChips;

    private ExecutorService io;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int generation = 0;
    private boolean loading = false;
    private boolean hasMore = true;
    private boolean liveInFlight = false;
    private long minId = Long.MAX_VALUE;
    private long maxId = 0;
    private long currentTotal = 0;
    private long lastCountMs = 0;
    private boolean chipGuard = false;
    private volatile boolean destroyed = false;

    private Runnable searchDebounce;
    private final Runnable liveTask = this::pullLive;
    private BroadcastReceiver liveReceiver;

    private static ExecutorService newIo() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "logs-io");
            t.setDaemon(true);
            return t;
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);
        store = LogStore.from(context);
        return inflater.inflate(R.layout.logger_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        destroyed = false;
        io = newIo();

        progress = view.findViewById(R.id.progress_indicator);
        recyclerView = view.findViewById(R.id.logger_list);
        emptyState = view.findViewById(R.id.empty_state);
        subtitle = view.findViewById(R.id.logs_subtitle);
        search = view.findViewById(R.id.log_search);
        searchClear = view.findViewById(R.id.log_search_clear);
        levelChips = view.findViewById(R.id.level_chips);
        toolChips = view.findViewById(R.id.tool_chips);

        view.findViewById(R.id.back).setOnClickListener(v -> goBack());
        view.findViewById(R.id.clear).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.save).setOnClickListener(v -> saveLogs());
        view.findViewById(R.id.share).setOnClickListener(v -> shareLogs());

        layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemViewCacheSize(40);
        adapter = new LoggerAdapter(context, null);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int total = layoutManager.getItemCount();
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= total - PREFETCH) loadMore();
            }
        });

        setupSearch();
        setupLevelChips();
        setupToolChips();

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerLive();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterLive();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        destroyed = true;
        unregisterLive();
        ui.removeCallbacksAndMessages(null);
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                searchClear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (searchDebounce != null) ui.removeCallbacks(searchDebounce);
                searchDebounce = () -> {
                    filter.search = text;
                    reload();
                };
                ui.postDelayed(searchDebounce, SEARCH_DEBOUNCE_MS);
            }
        });
        searchClear.setOnClickListener(v -> search.setText(""));
    }

    private void setupLevelChips() {
        ChipGroup.OnCheckedStateChangeListener rebuild = (group, ids) -> {
            if (chipGuard) return;
            filter.levels.clear();
            if (((Chip) group.findViewById(R.id.chip_cmd)).isChecked()) filter.levels.add(LogEntry.CMD);
            if (((Chip) group.findViewById(R.id.chip_out)).isChecked()) filter.levels.add(LogEntry.OUT);
            if (((Chip) group.findViewById(R.id.chip_err)).isChecked()) filter.levels.add(LogEntry.ERR);
            if (((Chip) group.findViewById(R.id.chip_info)).isChecked()) {
                filter.levels.add(LogEntry.INFO);
                filter.levels.add(LogEntry.WARN);
                filter.levels.add(LogEntry.SUCCESS);
            }
            reload();
        };
        levelChips.setOnCheckedStateChangeListener(rebuild);
    }

    private void setupToolChips() {
        toolChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (chipGuard) return;
            int checked = ids.isEmpty() ? R.id.chip_tool_all : ids.get(0);
            if (checked == R.id.chip_tool_all) {
                filter.tool = null;
            } else {
                Chip c = group.findViewById(checked);
                filter.tool = c != null && c.getTag() != null ? c.getTag().toString() : null;
            }
            reload();
        });
        submit(() -> {
            List<String> tools = store.distinctTools();
            ui.post(() -> {
                if (destroyed || !isAdded()) return;
                for (String tool : tools) {
                    Chip c = new Chip(context);
                    c.setText(tool);
                    c.setTag(tool);
                    c.setCheckable(true);
                    c.setTextSize(12f);
                    toolChips.addView(c);
                }
            });
        });
    }

    private void reload() {
        generation++;
        final int gen = generation;
        loading = true;
        hasMore = true;
        liveInFlight = false;
        minId = Long.MAX_VALUE;
        maxId = 0;
        progress.setVisibility(View.VISIBLE);
        adapter.setHighlight(filter.search);

        final LogFilter snapshot = filter.copy();
        submit(() -> {
            final List<LogEntry> page = store.query(snapshot, Long.MAX_VALUE, PAGE);
            final long total = store.count(snapshot);
            ui.post(() -> {
                if (destroyed || gen != generation) return;
                adapter.setItems(page);
                if (!page.isEmpty()) {
                    maxId = page.get(0).id;
                    minId = page.get(page.size() - 1).id;
                }
                hasMore = page.size() == PAGE;
                loading = false;
                currentTotal = total;
                lastCountMs = SystemClock.uptimeMillis();
                progress.setVisibility(View.GONE);
                emptyState.setVisibility(page.isEmpty() ? View.VISIBLE : View.GONE);
                updateSubtitle();
                recyclerView.scrollToPosition(0);
            });
        });
    }

    private void loadMore() {
        if (loading || !hasMore) return;
        loading = true;
        final int gen = generation;
        final long before = minId;
        final LogFilter snapshot = filter.copy();
        submit(() -> {
            final List<LogEntry> older = store.query(snapshot, before, PAGE);
            ui.post(() -> {
                if (destroyed || gen != generation) return;
                if (!older.isEmpty()) {
                    adapter.append(older);
                    minId = older.get(older.size() - 1).id;
                }
                hasMore = older.size() == PAGE;
                loading = false;
            });
        });
    }

    private void pullLive() {
        if (destroyed || liveInFlight) return;
        if (adapter.getItemCount() == 0) {
            if (!loading) reload();
            return;
        }
        liveInFlight = true;
        final int gen = generation;
        final long after = maxId;
        final boolean atTop = layoutManager.findFirstCompletelyVisibleItemPosition() <= 0;
        final LogFilter snapshot = filter.copy();
        final boolean resync = SystemClock.uptimeMillis() - lastCountMs > COUNT_RESYNC_MS;
        if (resync) lastCountMs = SystemClock.uptimeMillis();
        submit(() -> {
            final List<LogEntry> newer = store.queryNewer(snapshot, after, LIVE_CAP);
            final long total = resync ? store.count(snapshot) : -1;
            ui.post(() -> {
                liveInFlight = false;
                if (destroyed || gen != generation) return;
                if (total >= 0) currentTotal = total;
                if (!newer.isEmpty()) {
                    adapter.prepend(newer);
                    maxId = newer.get(0).id;
                    if (total < 0) currentTotal += newer.size();
                    if (minId == Long.MAX_VALUE) minId = newer.get(newer.size() - 1).id;
                    emptyState.setVisibility(View.GONE);
                    if (atTop) {
                        recyclerView.scrollToPosition(0);
                        if (adapter.getItemCount() > MAX_VIEW_ROWS) {
                            adapter.trimTail(MAX_VIEW_ROWS);
                            LogEntry oldest = adapter.last();
                            if (oldest != null) minId = oldest.id;
                            hasMore = true;
                        }
                    }
                }
                updateSubtitle();
                if (newer.size() >= LIVE_CAP) ui.post(liveTask);
            });
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateSubtitle() {
        String count = String.format(Locale.US, "%,d", Math.max(0, currentTotal));
        boolean filtered = !filter.isEmpty();
        subtitle.setText(filtered
                ? getString(R.string.logs_count_filtered, count)
                : getString(R.string.logs_count, count));
    }

    private void registerLive() {
        if (liveReceiver != null) return;
        liveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                ui.removeCallbacks(liveTask);
                ui.postDelayed(liveTask, LIVE_DEBOUNCE_MS);
            }
        };
        ContextCompat.registerReceiver(context.getApplicationContext(), liveReceiver,
                new IntentFilter(LogStore.ACTION_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterLive() {
        if (liveReceiver == null) return;
        try {
            context.getApplicationContext().unregisterReceiver(liveReceiver);
        } catch (Throwable ignored) {
        }
        liveReceiver = null;
        ui.removeCallbacks(liveTask);
    }

    private void clearLogs() {
        submit(() -> {
            store.clear();
            ui.post(() -> {
                if (destroyed) return;
                core.toaster(getString(R.string.logs_cleared));
                resetToolFilter();
                reload();
            });
        });
    }

    private void resetToolFilter() {
        chipGuard = true;
        java.util.List<View> toRemove = new java.util.ArrayList<>();
        for (int i = 0; i < toolChips.getChildCount(); i++) {
            View child = toolChips.getChildAt(i);
            if (child.getId() != R.id.chip_tool_all) toRemove.add(child);
        }
        for (View v : toRemove) toolChips.removeView(v);
        ((Chip) toolChips.findViewById(R.id.chip_tool_all)).setChecked(true);
        filter.tool = null;
        chipGuard = false;
    }

    private void saveLogs() {
        final LogFilter snapshot = filter.copy();
        submit(() -> {
            long n = exportToSdcard(snapshot);
            ui.post(() -> {
                if (destroyed) return;
                core.toaster(n >= 0
                        ? getString(R.string.logs_saved, EXPORT_SDCARD)
                        : getString(R.string.logs_save_failed));
            });
        });
    }

    private void shareLogs() {
        final LogFilter snapshot = filter.copy();
        submit(() -> {
            long n = exportToSdcard(snapshot);
            ui.post(() -> {
                if (destroyed || !isAdded()) return;
                if (n < 0) {
                    core.toaster(getString(R.string.logs_save_failed));
                    return;
                }
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/*");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + EXPORT_SDCARD));
                intent.putExtra(Intent.EXTRA_SUBJECT, "Stryker log");
                intent.putExtra(Intent.EXTRA_TEXT, "Stryker log at: " + sdf.format(new Date()));
                startActivity(Intent.createChooser(intent, getString(R.string.logs_share)));
            });
        });
    }

    private long exportToSdcard(LogFilter snapshot) {
        File out = new File(context.getFilesDir(), "stryker_export.log");
        long n = store.export(out, snapshot);
        if (n < 0) return -1;
        core.customCommand("mkdir -p /sdcard/Stryker && cp " + out.getAbsolutePath() + " " + EXPORT_SDCARD);
        return n;
    }

    private void goBack() {
        if (getParentFragment() instanceof SettingsNew) {
            getParentFragmentManager().popBackStack();
        } else {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.flContent, new SettingsNew()).commit();
        }
    }

    private void submit(Runnable r) {
        ExecutorService ex = io;
        if (destroyed || ex == null) return;
        try {
            ex.execute(r);
        } catch (RejectedExecutionException ignored) {
        }
    }
}
