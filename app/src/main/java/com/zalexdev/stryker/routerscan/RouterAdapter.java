package com.zalexdev.stryker.routerscan;


import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.utils.Core;

import java.util.List;

public class RouterAdapter extends RecyclerView.Adapter<RouterAdapter.ViewHolder> {
    public Context context;
    public Activity activity;
    public Core core;
    public List<Router> routers;

    public RouterAdapter(Context context2, Activity mActivity, List<Router> results) {
        context = context2;
        activity = mActivity;
        core = new Core(context2);
        routers = results;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.routerscan_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Router r = routers.get(position);
        holder.ip.setText(r.getIp());
        if (r.getStatus() != null && !r.getStatus().isEmpty()) {
            holder.status.setText(r.getStatus());
        }

        shrinkIcon(holder.ssid);
        shrinkIcon(holder.psk);
        shrinkIcon(holder.auth);
        bindResult(holder, r);

        holder.itemView.setOnClickListener(v -> {
            if (activity instanceof com.zalexdev.stryker.MainActivity) {
                androidx.fragment.app.Fragment f = ((com.zalexdev.stryker.MainActivity) activity)
                        .getSupportFragmentManager().findFragmentById(com.zalexdev.stryker.R.id.flContent);
                if (f instanceof RouterScanMain) {
                    ((RouterScanMain) f).openTerminal(r.getIp());
                }
            }
        });
    }

    private void bindResult(@NonNull ViewHolder h, @NonNull Router r) {
        int color = R.color.yellow;
        if (r.isScanned()) {
            if (r.getType() == 1) color = R.color.green;
            else if (r.getType() == 2) color = R.color.red;
        }
        h.progress.setColorFilter(ContextCompat.getColor(context, color));

        boolean cracked = r.getType() == 1;
        if (cracked) {
            h.ssid.setText(r.getSsid());
            h.psk.setText(r.getPsk());
            h.auth.setText(r.getAuth());
        }
        int vis = cracked ? View.VISIBLE : View.GONE;
        h.ssid.setVisibility(vis);
        h.psk.setVisibility(vis);
        h.auth.setVisibility(vis);
    }

    private void shrinkIcon(TextView tv) {
        int size = Math.round(15 * context.getResources().getDisplayMetrics().density);
        android.graphics.drawable.Drawable[] d = tv.getCompoundDrawablesRelative();
        if (d[0] != null) {
            d[0].setBounds(0, 0, size, size);
            tv.setCompoundDrawablesRelative(d[0], null, null, null);
        }
    }

    @Override
    public int getItemCount() {
        return routers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView ip;
        public TextView status;
        public TextView model;
        public TextView auth;
        public TextView ssid;
        public TextView macadr;
        public TextView psk;
        public ImageView progress;

        public ViewHolder(View v) {
            super(v);
            ip = v.findViewById(R.id.router_ip);
            model = v.findViewById(R.id.router_model);
            auth = v.findViewById(R.id.router_auth);
            ssid = v.findViewById(R.id.router_ssid);
            macadr = v.findViewById(R.id.router_mac);
            psk = v.findViewById(R.id.router_password);
            status = v.findViewById(R.id.router_progress);
            progress = v.findViewById(R.id.routerscan_prog);
        }
    }
}
