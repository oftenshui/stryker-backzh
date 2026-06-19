package com.zalexdev.stryker.nuclei;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.NucleiItem;

import java.net.URI;
import java.util.List;

public class FindingsAdapter extends RecyclerView.Adapter<FindingsAdapter.VH> {

    private static final int[] SEVERITY_COLORS = {
            0xFF1565C0,
            0xFFF9A825,
            0xFFEF6C00,
            0xFFD32F2F,
            0xFFB71C1C
    };

    private static final String[] SEVERITY_LABELS = {
            "INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL"
    };

    private final List<NucleiItem> items;

    public FindingsAdapter(List<NucleiItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nuclei_finding, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NucleiItem item = items.get(position);
        int sev = clamp(item.severity, 0, SEVERITY_COLORS.length - 1);
        int color = SEVERITY_COLORS[sev];

        h.title.setText(isBlank(item.title) ? "(untitled finding)" : item.title);
        h.host.setText(item.host);
        h.description.setText(isBlank(item.description) ? "—" : item.description.trim());
        h.sevChip.setText(SEVERITY_LABELS[sev]);
        h.sevChip.setTextColor(color);
        h.sevBar.setBackgroundColor(color);
        if (h.sevChip.getBackground() != null) {
            h.sevChip.getBackground().mutate().setTint(color);
            h.sevChip.getBackground().setAlpha(40);
        }

        bindExtracted(h, item.results);
        bindTags(h, item.tags);
        bindCve(h, item.cve);
    }

    private void bindExtracted(VH h, String raw) {
        String cleaned = prettyExtracted(raw);
        if (cleaned == null) {
            h.extractedCard.setVisibility(View.GONE);
            return;
        }
        h.extractedCard.setVisibility(View.VISIBLE);
        h.extracted.setText(cleaned);
    }

    private static String prettyExtracted(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("[]") || s.equals("\"\"")) return null;
        if (s.startsWith("[\"") && s.endsWith("\"]")) {
            s = s.substring(2, s.length() - 2);
        } else if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\",\"", "\n");
    }

    private void bindTags(VH h, java.util.ArrayList<String> tags) {
        h.tagsGroup.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            h.tagsGroup.setVisibility(View.GONE);
            return;
        }
        h.tagsGroup.setVisibility(View.VISIBLE);
        for (String tag : tags) {
            String label = shortenTag(tag);
            if (label.isEmpty()) continue;
            Chip chip = new Chip(h.tagsGroup.getContext());
            chip.setText(label);
            chip.setTextSize(10);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(0f);
            chip.setChipBackgroundColor(ColorStateList.valueOf(0x14000000));
            chip.setChipStrokeWidth(0f);
            chip.setClickable(false);
            chip.setFocusable(false);
            h.tagsGroup.addView(chip);
        }
    }

    private static String shortenTag(String tag) {
        if (tag == null) return "";
        String t = tag.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            try {
                URI uri = URI.create(t);
                return uri.getHost() != null ? uri.getHost() : t;
            } catch (IllegalArgumentException e) {
                return t;
            }
        }
        return t;
    }

    private void bindCve(VH h, String cve) {
        if (isBlank(cve)) {
            h.cveChip.setVisibility(View.GONE);
            return;
        }
        h.cveChip.setVisibility(View.VISIBLE);
        h.cveChip.setText(cve);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView host;
        final TextView description;
        final TextView sevChip;
        final View sevBar;
        final View extractedCard;
        final TextView extracted;
        final ChipGroup tagsGroup;
        final TextView cveChip;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.finding_title);
            host = itemView.findViewById(R.id.finding_host);
            description = itemView.findViewById(R.id.finding_description);
            sevChip = itemView.findViewById(R.id.finding_sev_chip);
            sevBar = itemView.findViewById(R.id.finding_sev_bar);
            extractedCard = itemView.findViewById(R.id.finding_extracted_card);
            extracted = itemView.findViewById(R.id.finding_extracted);
            tagsGroup = itemView.findViewById(R.id.finding_tags);
            cveChip = itemView.findViewById(R.id.finding_cve);
        }
    }
}
