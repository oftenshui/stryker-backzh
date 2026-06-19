package com.zalexdev.stryker.hid.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.hid.payload.Payload;

import java.util.ArrayList;
import java.util.List;

public final class PayloadCardAdapter extends RecyclerView.Adapter<PayloadCardAdapter.ViewHolder> {

    public interface OnPayloadClicked {
        void onClick(@NonNull Payload payload);
        default void onMore(@NonNull Payload payload, @NonNull android.view.View anchor) {}
    }

    private final OnPayloadClicked callback;
    private final List<Payload> items = new ArrayList<>();

    public PayloadCardAdapter(@NonNull OnPayloadClicked callback) {
        this.callback = callback;
    }

    public void submit(@NonNull List<Payload> items) {
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payload_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Payload p = items.get(pos);
        h.name.setText(p.displayName);
        boolean sample = p.source == Payload.Source.ASSET;
        h.sourcePill.setText(sample ? "SAMPLE" : "SAVED");
        int lineCount = countLines(p.body);
        h.meta.setText((sample ? "sample" : "saved") + " · " + lineCount + " lines");
        h.preview.setText(previewSnippet(p.body));
        h.itemView.setOnClickListener(v -> callback.onClick(p));
        if (h.more != null) {
            h.more.setOnClickListener(v -> callback.onMore(p, v));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int countLines(String body) {
        if (body == null || body.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static String previewSnippet(String body) {
        if (body == null) return "";
        String[] lines = body.split("\\R", 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i].length() > 64 ? lines[i].substring(0, 64) + "…" : lines[i]);
        }
        return sb.toString();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView name;
        final MaterialTextView meta;
        final MaterialTextView sourcePill;
        final MaterialTextView preview;
        final android.view.View more;

        ViewHolder(@NonNull View v) {
            super(v);
            name       = v.findViewById(R.id.payload_name);
            meta       = v.findViewById(R.id.payload_meta);
            sourcePill = v.findViewById(R.id.payload_source_pill);
            preview    = v.findViewById(R.id.payload_preview);
            more       = v.findViewById(R.id.payload_more_btn);
        }
    }
}
