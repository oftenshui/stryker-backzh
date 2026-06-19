package com.zalexdev.stryker.nuclei;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Site;

import java.util.ArrayList;
import java.util.List;

public class TargetsAdapter extends RecyclerView.Adapter<TargetsAdapter.VH> {

    public interface Listener {
        void onOpen(int siteIndex, Site site);
        void onStop(int siteIndex, Site site);
        void onRescan(int siteIndex, Site site);
        void onMore(int siteIndex, Site site, View anchor);
    }

    private final Context context;
    private final Listener listener;
    private List<Site> sites = new ArrayList<>();

    private final List<Integer> indexMap = new ArrayList<>();

    public TargetsAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Site> sites, List<Integer> indexMap) {
        this.sites = sites != null ? sites : new ArrayList<>();
        this.indexMap.clear();
        this.indexMap.addAll(indexMap);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nuclei_target, parent, false);
        return new VH(v);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= indexMap.size()) return RecyclerView.NO_ID;
        Site s = sites.get(position);
        return ((long) indexMap.get(position) << 32) ^ (s.getUrl() != null ? s.getUrl().hashCode() : 0);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Site s = sites.get(position);
        int srcIndex = indexMap.get(position);

        h.url.setText(s.getUrl());
        State state = State.from(s.status);
        bindStatus(h, s, state);
        bindSeverityPills(h, s);

        h.btnStop.setVisibility(state == State.RUNNING ? View.VISIBLE : View.GONE);

        h.root.setOnClickListener(v -> listener.onOpen(srcIndex, s));
        h.btnStop.setOnClickListener(v -> listener.onStop(srcIndex, s));
        h.btnRescan.setOnClickListener(v -> listener.onRescan(srcIndex, s));
        h.btnMore.setOnClickListener(v -> listener.onMore(srcIndex, s, v));
    }

    private void bindStatus(VH h, Site s, State state) {
        int color;
        String chipText;
        switch (state) {
            case RUNNING:
                int progress = parseProgress(s.progress);
                color = ContextCompat.getColor(context, R.color.stryker_accent);
                chipText = "RUN";
                h.status.setText(context.getString(R.string.nuclei_status_progress, String.valueOf(progress)));
                h.progress.setVisibility(View.VISIBLE);
                if (progress > 0) {
                    h.progress.setIndeterminate(false);
                    h.progress.setProgressCompat(progress, true);
                } else {
                    h.progress.setIndeterminate(true);
                }
                break;
            case FINISHED:
                color = ContextCompat.getColor(context, R.color.green);
                chipText = "DONE";
                h.status.setText(R.string.nuclei_status_finished);
                h.progress.setVisibility(View.GONE);
                break;
            case FAILED:
                color = ContextCompat.getColor(context, R.color.red);
                chipText = "FAIL";
                h.status.setText(R.string.nuclei_status_failed);
                h.progress.setVisibility(View.GONE);
                break;
            case SCHEDULED:
            default:
                color = ContextCompat.getColor(context, R.color.grey);
                chipText = "QUEUE";
                h.status.setText(R.string.nuclei_status_scheduled);
                h.progress.setVisibility(View.GONE);
                break;
        }
        h.stateChip.setText(chipText);
        h.stateChip.setTextColor(color);
        if (h.stateChip.getBackground() != null) {
            h.stateChip.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            h.stateChip.getBackground().setAlpha(40);
        }
    }

    private void bindSeverityPills(VH h, Site s) {
        int[] counts = s.vulnsCount != null && s.vulnsCount.length >= 4 ? s.vulnsCount : new int[]{0,0,0,0};
        applyPill(h.sevCritical, "C", counts.length > 4 ? counts[4] : 0, 0xFFB71C1C);
        applyPill(h.sevHigh,     "H", counts[3], 0xFFD32F2F);
        applyPill(h.sevMedium,   "M", counts[2], 0xFFEF6C00);
        applyPill(h.sevLow,      "L", counts[1], 0xFFF9A825);
        applyPill(h.sevInfo,     "I", counts[0], 0xFF1565C0);
    }

    private void applyPill(VH.Pill pill, String letter, int count, int color) {
        pill.label.setText(letter + " " + count);
        if (count == 0) {
            pill.label.setTextColor(ContextCompat.getColor(context, R.color.grey));
            pill.dot.getBackground().setColorFilter(
                    ContextCompat.getColor(context, R.color.grey), PorterDuff.Mode.SRC_IN);
        } else {
            pill.label.setTextColor(color);
            pill.dot.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    @Override
    public int getItemCount() {
        return sites.size();
    }

    static int parseProgress(String p) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(p))); }
        catch (NumberFormatException e) { return 0; }
    }

    enum State {
        SCHEDULED, RUNNING, FINISHED, FAILED;

        static State from(String s) {
            if (s == null) return SCHEDULED;
            switch (s) {
                case "Running": return RUNNING;
                case "Finished": return FINISHED;
                case "Failed": return FAILED;
                default: return SCHEDULED;
            }
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView url, status, stateChip;
        final LinearProgressIndicator progress;
        final MaterialButton btnStop, btnRescan;
        final ImageView btnMore;
        final Pill sevCritical, sevHigh, sevMedium, sevLow, sevInfo;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.target_root);
            icon = itemView.findViewById(R.id.target_icon);
            url = itemView.findViewById(R.id.target_url);
            status = itemView.findViewById(R.id.target_status);
            stateChip = itemView.findViewById(R.id.target_state_chip);
            progress = itemView.findViewById(R.id.target_progress);
            btnStop = itemView.findViewById(R.id.target_btn_stop);
            btnRescan = itemView.findViewById(R.id.target_btn_rescan);
            btnMore = itemView.findViewById(R.id.target_btn_more);
            sevCritical = Pill.bind(itemView.findViewById(R.id.target_sev_critical));
            sevHigh = Pill.bind(itemView.findViewById(R.id.target_sev_high));
            sevMedium = Pill.bind(itemView.findViewById(R.id.target_sev_medium));
            sevLow = Pill.bind(itemView.findViewById(R.id.target_sev_low));
            sevInfo = Pill.bind(itemView.findViewById(R.id.target_sev_info));
        }

        static class Pill {
            final View dot;
            final TextView label;
            Pill(View dot, TextView label) { this.dot = dot; this.label = label; }
            static Pill bind(View root) {
                return new Pill(
                        root.findViewById(R.id.sev_dot),
                        root.findViewById(R.id.sev_label));
            }
        }
    }
}
