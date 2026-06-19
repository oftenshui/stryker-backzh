package com.zalexdev.stryker.license;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.License;
import com.zalexdev.stryker.utils.Core;

import java.util.List;

public class LicenseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_ITEM = 1;

    private final List<License> items;
    private final Activity activity;
    private final Core core;

    public LicenseAdapter(Context context, Activity activity, List<License> licenses) {
        this.items = licenses;
        this.activity = activity;
        this.core = new Core(context);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).header ? TYPE_SECTION : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION) {
            return new SectionVH(inflater.inflate(R.layout.license_section, parent, false));
        }
        return new ItemVH(inflater.inflate(R.layout.license_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        License l = items.get(position);
        if (holder instanceof SectionVH) {
            ((SectionVH) holder).title.setText(l.name);
            return;
        }
        ItemVH vh = (ItemVH) holder;
        vh.name.setText(l.name);
        vh.desc.setText(l.desc);
        if (l.copyright != null && !l.copyright.isEmpty()) {
            vh.copyright.setText(l.copyright);
            vh.copyright.setVisibility(View.VISIBLE);
        } else {
            vh.copyright.setVisibility(View.GONE);
        }
        boolean hasUrl = l.url != null && !l.url.isEmpty();
        vh.link.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
        vh.card.setClickable(hasUrl);
        vh.card.setOnClickListener(hasUrl ? v -> openLink(l.url) : null);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void openLink(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Exception e) {
            core.toaster(activity, "No browser available to open " + url);
        }
    }

    static final class SectionVH extends RecyclerView.ViewHolder {
        final TextView title;

        SectionVH(@NonNull View v) {
            super(v);
            title = (TextView) v;
        }
    }

    static final class ItemVH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView desc;
        final TextView copyright;
        final ImageView link;
        final MaterialCardView card;

        ItemVH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            name = v.findViewById(R.id.license_name);
            desc = v.findViewById(R.id.license_desc);
            copyright = v.findViewById(R.id.license_copyright);
            link = v.findViewById(R.id.license_link);
        }
    }
}
