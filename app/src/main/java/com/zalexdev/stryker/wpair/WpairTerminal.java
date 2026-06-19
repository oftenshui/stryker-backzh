package com.zalexdev.stryker.wpair;

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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WpairTerminal extends Fragment {

    private static final int MAX_LINES = 5000;
    private static final String ARG_FILTER = "host";

    private static final String PREF_FILTER = "wpair_term_filter";
    private static final String PREF_MANUAL_SCROLL = "wpair_term_manual_scroll";

    private File logFile;
    private long bytesRead = 0;

    private TextView subtitle;
    private ProgressBar liveDot;
    private RecyclerView recycler;
    private View emptyView;
    private EditText filterInput;
    private LineAdapter adapter;
    private LinearLayoutManager layoutManager;

    private String filterTerm = "";
    private boolean autoScroll = true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            readNewLines();
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver onUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            readNewLines();
        }
    };

    public static WpairTerminal forHost(String host) {
        WpairTerminal t = new WpairTerminal();
        Bundle b = new Bundle();
        if (host != null) b.putString(ARG_FILTER, host);
        t.setArguments(b);
        return t;
    }

    public static WpairTerminal newInstance() {
        return new WpairTerminal();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wpair_terminal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        logFile = WpairLog.logFile(requireContext());

        subtitle = view.findViewById(R.id.wpair_terminal_subtitle);
        liveDot = view.findViewById(R.id.wpair_terminal_live);
        recycler = view.findViewById(R.id.wpair_terminal_recycler);
        emptyView = view.findViewById(R.id.wpair_terminal_empty);
        filterInput = view.findViewById(R.id.wpair_terminal_filter);

        subtitle.setText(logFile.getName());

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
                autoScroll = (total == 0) || (last >= total - 2);
                Context ctx = getContext();
                if (ctx != null) new Core(ctx).putBoolean(PREF_MANUAL_SCROLL, !autoScroll);
            }
        });

        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterTerm = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                adapter.applyFilter(filterTerm);
                Context ctx = getContext();
                if (ctx != null) new Core(ctx).putString(PREF_FILTER, filterTerm);
            }
        });

        view.<MaterialButton>findViewById(R.id.wpair_terminal_back)
                .setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        view.<MaterialButton>findViewById(R.id.wpair_terminal_copy)
                .setOnClickListener(v -> copyAll());
        view.<MaterialButton>findViewById(R.id.wpair_terminal_share)
                .setOnClickListener(v -> shareLog());
        view.<MaterialButton>findViewById(R.id.wpair_terminal_clear)
                .setOnClickListener(v -> {
                    WpairLog.truncate(requireContext());
                    bytesRead = 0;
                    adapter.clear();
                    Toast.makeText(getContext(), "Log cleared", Toast.LENGTH_SHORT).show();
                });

        Core core = new Core(getContext());
        autoScroll = !core.getBoolean(PREF_MANUAL_SCROLL);

        String argHost = "";
        if (getArguments() != null) {
            argHost = getArguments().getString(ARG_FILTER, "");
        }
        String restoreFilter = !argHost.isEmpty() ? argHost : core.getString(PREF_FILTER);
        if (restoreFilter != null && !restoreFilter.isEmpty()) {
            filterInput.setText(restoreFilter);
            filterInput.setSelection(filterInput.getText().length());
        }

        readNewLines();
        adapter.applyFilter(filterTerm);
        liveDot.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(WpairLog.ACTION_UPDATED);
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
        } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacks(pollTick);
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
                fresh.add(Line.parse(line));
            }
            bytesRead = size;
        } catch (IOException ignored) {}
        if (fresh.isEmpty()) return;
        adapter.append(fresh);
        showEmpty(adapter.getVisibleCount() == 0);
        if (autoScroll) recycler.scrollToPosition(adapter.getItemCount() - 1);
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
        cm.setPrimaryClip(ClipData.newPlainText("wpair log", sb.toString()));
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
        share.putExtra(Intent.EXTRA_SUBJECT, "wpair terminal");
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Share log"));
    }

    private static final class Line {
        final String timestamp;
        final String tag;
        final String host;
        final String body;

        Line(String timestamp, String tag, String host, String body) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.host = host;
            this.body = body;
        }

        static Line parse(String raw) {
            if (raw == null) return new Line("", "INFO", "", "");
            int firstSpace = raw.indexOf(' ');
            if (firstSpace < 0) return new Line("", "INFO", "", raw);
            String ts = raw.substring(0, firstSpace);
            int tagOpen = raw.indexOf('[', firstSpace);
            int tagClose = raw.indexOf(']', tagOpen);
            if (tagOpen < 0 || tagClose < 0) return new Line(ts, "INFO", "", raw.substring(firstSpace + 1));
            String tag = raw.substring(tagOpen + 1, tagClose).trim();
            int hostOpen = raw.indexOf('[', tagClose);
            int hostClose = raw.indexOf(']', hostOpen);
            String host = "";
            String body;
            if (hostOpen < 0 || hostClose < 0) {
                body = (tagClose + 2 <= raw.length()) ? raw.substring(tagClose + 2) : "";
            } else {
                host = raw.substring(hostOpen + 1, hostClose).trim();
                body = (hostClose + 2 <= raw.length()) ? raw.substring(hostClose + 2) : "";
            }
            return new Line(ts, tag, host, body);
        }
    }

    private final class LineAdapter extends RecyclerView.Adapter<LineAdapter.VH> {
        private final ArrayList<Line> all = new ArrayList<>();
        private final ArrayList<Line> visible = new ArrayList<>();

        void clear() {
            int n = visible.size();
            all.clear();
            visible.clear();
            if (n > 0) notifyItemRangeRemoved(0, n);
        }

        int getVisibleCount() {
            return visible.size();
        }

        void append(List<Line> fresh) {
            all.addAll(fresh);
            if (all.size() > MAX_LINES) {
                int drop = all.size() - MAX_LINES;
                all.subList(0, drop).clear();
            }
            applyFilter(filterTerm);
        }

        void applyFilter(String term) {
            int oldSize = visible.size();
            visible.clear();
            if (term == null || term.isEmpty()) {
                visible.addAll(all);
            } else {
                for (Line l : all) {
                    if (matches(l, term)) visible.add(l);
                }
            }
            notifyItemRangeRemoved(0, oldSize);
            notifyItemRangeInserted(0, visible.size());
            showEmpty(visible.isEmpty());
        }

        private boolean matches(Line l, String term) {
            if (l.host != null && l.host.toLowerCase(Locale.ROOT).contains(term)) return true;
            if (l.body != null && l.body.toLowerCase(Locale.ROOT).contains(term)) return true;
            return l.tag != null && l.tag.toLowerCase(Locale.ROOT).contains(term);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_router_terminal_line, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Line l = visible.get(position);
            h.timestamp.setText(l.timestamp);
            h.tag.setText(l.tag);
            h.host.setText(l.host);
            h.host.setVisibility(l.host.isEmpty() ? View.GONE : View.VISIBLE);
            switch (l.tag) {
                case "ERR":
                    h.tag.setTextColor(0xFFFF7B72);
                    h.body.setTextColor(0xFFFFA198);
                    break;
                case "OUT":
                    h.tag.setTextColor(0xFF7EE787);
                    h.body.setTextColor(0xFFE6EDF3);
                    break;
                case "INFO":
                default:
                    h.tag.setTextColor(0xFFD2A8FF);
                    h.body.setTextColor(0xFFD2A8FF);
                    break;
            }
            h.body.setText(l.body);
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView timestamp, tag, host, body;
            VH(@NonNull View itemView) {
                super(itemView);
                timestamp = itemView.findViewById(R.id.term_timestamp);
                tag = itemView.findViewById(R.id.term_chip);
                host = itemView.findViewById(R.id.term_host);
                body = itemView.findViewById(R.id.term_body);
            }
        }
    }
}
