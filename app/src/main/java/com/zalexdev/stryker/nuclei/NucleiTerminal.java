package com.zalexdev.stryker.nuclei;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Site;
import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NucleiTerminal extends Fragment {

    private static final String ARG_INDEX = "siteIndex";
    private static final int MAX_LINES = 5000;

    private static final String PREF_AUTOSCROLL = "nuclei_term_autoscroll";
    private static final String PREF_FILTER = "nuclei_term_filter";

    private int siteIndex = -1;
    private Site site;
    private Core core;
    private File logFile;
    private long bytesRead = 0;

    private TextView title, subtitle;
    private ProgressBar liveDot;
    private RecyclerView recycler;
    private View emptyView;
    private LineAdapter adapter;
    private LinearLayoutManager layoutManager;

    private boolean autoScroll = true;
    private String filterTerm = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            readNewLines();
            updateLiveIndicator();
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver onUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int changed = intent.getIntExtra(NucleiScanService.EXTRA_SITE_ID, -1);
            if (changed == siteIndex) {
                readNewLines();
                updateLiveIndicator();
            }
        }
    };

    public static NucleiTerminal forSite(int siteIndex) {
        NucleiTerminal t = new NucleiTerminal();
        Bundle b = new Bundle();
        b.putInt(ARG_INDEX, siteIndex);
        t.setArguments(b);
        return t;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nuclei_terminal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        core = new Core(getContext());
        if (getArguments() != null) siteIndex = getArguments().getInt(ARG_INDEX, -1);

        ArrayList<Site> all = core.getSites();
        if (siteIndex >= 0 && siteIndex < all.size()) site = all.get(siteIndex);

        logFile = NucleiScanService.terminalLog(requireContext(), siteIndex);

        title = view.findViewById(R.id.terminal_title);
        subtitle = view.findViewById(R.id.terminal_subtitle);
        liveDot = view.findViewById(R.id.terminal_live);
        recycler = view.findViewById(R.id.terminal_recycler);
        emptyView = view.findViewById(R.id.terminal_empty);

        title.setText("nuclei terminal");
        subtitle.setText(site != null ? site.getUrl() : ("site " + siteIndex));

        String savedScroll = core.getString(PREF_AUTOSCROLL);
        if (!savedScroll.isEmpty()) autoScroll = "1".equals(savedScroll);
        filterTerm = core.getString(PREF_FILTER);

        layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);
        adapter = new LineAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int last = layoutManager.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                boolean wasAuto = autoScroll;
                autoScroll = (total == 0) || (last >= total - 2);
                if (autoScroll != wasAuto && core != null) {
                    core.putString(PREF_AUTOSCROLL, autoScroll ? "1" : "0");
                }
            }
        });

        view.<MaterialButton>findViewById(R.id.terminal_back)
                .setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        view.<MaterialButton>findViewById(R.id.terminal_copy)
                .setOnClickListener(v -> copyAll());
        view.<MaterialButton>findViewById(R.id.terminal_share)
                .setOnClickListener(v -> shareLog());

        readNewLines();
        updateLiveIndicator();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(NucleiScanService.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(onUpdate, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(onUpdate, f);
        }
        handler.postDelayed(pollTick, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(onUpdate);
        } catch (IllegalArgumentException ignored) {
        }
        handler.removeCallbacks(pollTick);
    }

    private void updateLiveIndicator() {
        ArrayList<Site> all = core.getSites();
        if (siteIndex >= 0 && siteIndex < all.size()) site = all.get(siteIndex);
        boolean running = site != null && ("Running".equals(site.status) || "Scheduled".equals(site.status));
        liveDot.setVisibility(running ? View.VISIBLE : View.GONE);
        if (site != null) {
            subtitle.setText(site.getUrl() + "  ·  " + (site.status != null ? site.status : "—"));
        }
    }

    private void readNewLines() {
        if (logFile == null || !logFile.exists()) {
            showEmpty(true);
            return;
        }
        long size = logFile.length();
        if (size < bytesRead) {
            adapter.clear();
            bytesRead = 0;
        }
        if (size == bytesRead) return;

        List<Line> fresh = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            long skipped = 0;
            while (skipped < bytesRead) {
                long n = br.skip(bytesRead - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            String line;
            while ((line = br.readLine()) != null) {
                Line parsed = Line.parse(line);
                if (matchesFilter(parsed)) fresh.add(parsed);
            }
            bytesRead = size;
        } catch (IOException e) {
        }
        if (fresh.isEmpty()) return;
        adapter.append(fresh);
        showEmpty(adapter.getItemCount() == 0);
        if (autoScroll) recycler.scrollToPosition(adapter.getItemCount() - 1);
    }

    public void setFilterTerm(String term) {
        filterTerm = term == null ? "" : term.trim();
        if (core != null) core.putString(PREF_FILTER, filterTerm);
        if (adapter != null) {
            adapter.clear();
            bytesRead = 0;
            readNewLines();
            updateLiveIndicator();
        }
    }

    private boolean matchesFilter(Line l) {
        if (filterTerm == null || filterTerm.isEmpty()) return true;
        if (l == null) return false;
        String needle = filterTerm.toLowerCase();
        if (l.body != null && l.body.toLowerCase().contains(needle)) return true;
        return l.tag != null && l.tag.toLowerCase().contains(needle);
    }

    private void showEmpty(boolean empty) {
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void copyAll() {
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(getContext(), "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException e) {
            Toast.makeText(getContext(), "Read failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("nuclei log", sb.toString()));
        Toast.makeText(getContext(), "Log copied", Toast.LENGTH_SHORT).show();
    }

    private void shareLog() {
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(getContext(), "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException ignored) {}
        share.putExtra(Intent.EXTRA_SUBJECT, "nuclei terminal: " + (site != null ? site.getUrl() : siteIndex));
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Share log"));
    }

    private static final class Line {
        final String timestamp;
        final String tag;
        final String body;

        Line(String timestamp, String tag, String body) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.body = body;
        }

        static Line parse(String raw) {
            if (raw == null) return new Line("", "OUT", "");
            int firstSpace = raw.indexOf(' ');
            if (firstSpace < 0) return new Line("", "OUT", raw);
            String ts = raw.substring(0, firstSpace);
            int open = raw.indexOf('[', firstSpace);
            int close = raw.indexOf(']', open);
            if (open < 0 || close < 0) return new Line(ts, "OUT", raw.substring(firstSpace + 1));
            String tag = raw.substring(open + 1, close).trim();
            String body = close + 2 <= raw.length() ? raw.substring(close + 2) : "";
            return new Line(ts, tag, body);
        }
    }

    private static final class LineAdapter extends RecyclerView.Adapter<LineAdapter.VH> {
        private final ArrayList<Line> lines = new ArrayList<>();

        void clear() {
            int n = lines.size();
            lines.clear();
            notifyItemRangeRemoved(0, n);
        }

        void append(List<Line> fresh) {
            int from = lines.size();
            lines.addAll(fresh);
            if (lines.size() > MAX_LINES) {
                int drop = lines.size() - MAX_LINES;
                lines.subList(0, drop).clear();
                notifyItemRangeRemoved(0, drop);
                notifyItemRangeInserted(0, lines.size());
            } else {
                notifyItemRangeInserted(from, fresh.size());
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nuclei_terminal_line, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Line l = lines.get(position);
            h.timestamp.setText(l.timestamp);
            h.tag.setText(l.tag);
            switch (l.tag) {
                case "ERR":
                    h.tag.setTextColor(0xFFFF7B72);
                    h.body.setTextColor(0xFFFFA198);
                    break;
                case "INFO":
                    h.tag.setTextColor(0xFFD2A8FF);
                    h.body.setTextColor(0xFFD2A8FF);
                    break;
                case "OUT":
                default:
                    h.tag.setTextColor(0xFF7EE787);
                    h.body.setTextColor(0xFFE6EDF3);
                    break;
            }
            h.body.setText(l.body);
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView timestamp, tag, body;
            VH(@NonNull View itemView) {
                super(itemView);
                timestamp = itemView.findViewById(R.id.term_timestamp);
                tag = itemView.findViewById(R.id.term_chip);
                body = itemView.findViewById(R.id.term_body);
            }
        }
    }
}
