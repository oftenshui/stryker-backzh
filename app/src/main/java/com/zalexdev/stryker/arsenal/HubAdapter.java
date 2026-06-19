package com.zalexdev.stryker.arsenal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Exploit;

import java.util.List;

public class HubAdapter extends RecyclerView.Adapter<HubAdapter.VH> {

    interface Listener {
        void onRun(Exploit exploit, int position);
    }

    private final Context context;
    private final List<Exploit> exploits;
    private final Listener listener;

    public HubAdapter(Context context, List<Exploit> exploits, Listener listener) {
        this.context = context;
        this.exploits = exploits;
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.arsenal_hub_item, parent, false);
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Exploit exploit = exploits.get(position);
        h.title.setText(exploit.getTitle());
        h.path.setText(exploit.getPath() + " " + safe(exploit.getArgs()));

        boolean isSystem = Boolean.TRUE.equals(exploit.getIssystem());
        if (isSystem) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(R.string.arsenal_hub_badge_system);
            h.badge.setBackgroundResource(R.drawable.arsenal_chip_blue);
            h.badge.setTextColor(0xFF0D47A1);
        } else {
            h.badge.setVisibility(View.GONE);
        }

        int tint;
        if ("Python".equalsIgnoreCase(exploit.getLang())) {
            tint = ContextCompat.getColor(context, R.color.stryker_accent);
        } else {
            tint = 0xFF6A1B9A;
        }
        h.icon.setColorFilter(tint);
        h.action.setColorFilter(tint);

        View.OnClickListener open = v -> {
            if (listener != null) listener.onRun(exploit, h.getBindingAdapterPosition());
        };
        h.card.setOnClickListener(open);
        h.action.setOnClickListener(open);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public int getItemCount() {
        return exploits.size();
    }

    @Override
    public long getItemId(int position) {
        Exploit e = exploits.get(position);
        if (e == null || e.getTitle() == null) return position;
        return e.getTitle().hashCode() ^ position;
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView path;
        final TextView badge;
        final ImageView icon;
        final ImageView action;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.hub_item_card);
            title = v.findViewById(R.id.hub_item_title);
            path = v.findViewById(R.id.hub_item_path);
            badge = v.findViewById(R.id.hub_item_badge);
            icon = v.findViewById(R.id.hub_item_icon);
            action = v.findViewById(R.id.hub_item_action);
        }
    }
}
