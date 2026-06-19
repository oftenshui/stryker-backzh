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
import com.zalexdev.stryker.custom.Sploit;

import java.util.List;

public class SploitAdapter extends RecyclerView.Adapter<SploitAdapter.VH> {

    interface Listener {
        void onOpen(Sploit sploit);
        void onQuickSave(Sploit sploit);
    }

    private final Context context;
    private final List<Sploit> results;
    private final Listener listener;

    public SploitAdapter(Context context, List<Sploit> results, Listener listener) {
        this.context = context;
        this.results = results;
        this.listener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.arsenal_sploit_item, parent, false);
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Sploit s = results.get(position);
        h.title.setText(s.getTitle() == null ? "" : s.getTitle());

        String author = s.getAuthor() == null ? "" : s.getAuthor();
        String date = s.getDate() == null ? "" : s.getDate();
        String meta;
        if (!author.isEmpty() && !date.isEmpty()) {
            meta = author + " · " + date;
        } else {
            meta = author + date;
        }
        h.meta.setText(meta);

        String platform = s.getPlatform() == null ? "" : s.getPlatform().toLowerCase();
        String type = s.getType() == null ? "" : s.getType().toLowerCase();
        h.platform.setText(platform.isEmpty() ? "—" : platform);
        h.type.setText(type.isEmpty() ? "—" : type);

        h.icon.setImageResource(iconFor(platform, type));
        h.icon.setColorFilter(ContextCompat.getColor(context, R.color.night_contrast));

        h.platform.setBackgroundResource(platformChipBg(platform));
        h.platform.setTextColor(platformChipColor(platform));
        h.type.setBackgroundResource(typeChipBg(type));
        h.type.setTextColor(typeChipColor(type));

        View.OnClickListener open = v -> {
            if (listener != null) listener.onOpen(s);
        };
        h.card.setOnClickListener(open);

        h.save.setOnClickListener(v -> {
            if (listener != null) listener.onQuickSave(s);
        });
    }

    private int iconFor(String platform, String type) {
        if (type.contains("webapps")) return R.drawable.web;
        if (platform.contains("windows")) return R.drawable.windows_icon;
        if (platform.contains("linux") || platform.contains("unix")) return R.drawable.linux;
        if (platform.contains("android")) return R.drawable.iphone;
        if (platform.contains("ios") || platform.contains("macos")) return R.drawable.apple;
        if (platform.contains("hardware")) return R.drawable.board;
        if (platform.contains("multiple")) return R.drawable.devices;
        if (platform.contains("freebsd")) return R.drawable.freebsd;
        if (platform.contains("solaris")) return R.drawable.solaris;
        return R.drawable.question;
    }

    private int platformChipBg(String platform) {
        if (platform.contains("linux") || platform.contains("unix") || platform.contains("freebsd"))
            return R.drawable.arsenal_chip_blue;
        if (platform.contains("windows")) return R.drawable.arsenal_chip_purple;
        if (platform.contains("android") || platform.contains("ios") || platform.contains("macos"))
            return R.drawable.arsenal_chip_green;
        return R.drawable.arsenal_chip_grey;
    }

    private int platformChipColor(String platform) {
        if (platform.contains("linux") || platform.contains("unix") || platform.contains("freebsd"))
            return 0xFF0D47A1;
        if (platform.contains("windows")) return 0xFF4A148C;
        if (platform.contains("android") || platform.contains("ios") || platform.contains("macos"))
            return 0xFF1B5E20;
        return 0xFF455A64;
    }

    private int typeChipBg(String type) {
        if (type.contains("remote") || type.contains("webapps")) return R.drawable.arsenal_chip_orange;
        if (type.contains("local")) return R.drawable.arsenal_chip_green;
        if (type.contains("dos")) return R.drawable.arsenal_chip_purple;
        return R.drawable.arsenal_chip_grey;
    }

    private int typeChipColor(String type) {
        if (type.contains("remote") || type.contains("webapps")) return 0xFFE65100;
        if (type.contains("local")) return 0xFF1B5E20;
        if (type.contains("dos")) return 0xFF4A148C;
        return 0xFF455A64;
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    @Override
    public long getItemId(int position) {
        Sploit s = results.get(position);
        if (s == null || s.getId() == null) return position;
        try {
            return Long.parseLong(s.getId());
        } catch (NumberFormatException ignored) {
            return s.getId().hashCode();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView meta;
        final TextView platform;
        final TextView type;
        final ImageView icon;
        final ImageView save;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.sploit_item_card);
            title = v.findViewById(R.id.sploit_item_title);
            meta = v.findViewById(R.id.sploit_item_meta);
            platform = v.findViewById(R.id.sploit_item_platform);
            type = v.findViewById(R.id.sploit_item_type);
            icon = v.findViewById(R.id.sploit_item_icon);
            save = v.findViewById(R.id.sploit_item_save);
        }
    }
}
