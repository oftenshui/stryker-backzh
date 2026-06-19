package com.zalexdev.stryker.localnetwork;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.Port;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;


public class PortAdapter extends RecyclerView.Adapter<PortAdapter.ViewHolder> {
    public Context context;
    public Activity activity;
    public Device device;
    public ArrayList<Port> ports;
    public Core core;


    public PortAdapter(Context context2, Activity mActivity, Device device) {
        context = context2;
        activity = mActivity;
        ports = device.getPorts();
        this.device = device;
        core = new Core(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.local_portview_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint({"UseCompatLoadingForDrawables", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder adapter, @SuppressLint("RecyclerView") final int position) {
        Port port = ports.get(position);
        adapter.port_num.setText(port.getPortNumber() + " (" + port.getPortName() + ")");
        if (port.getBanner() != null && port.getBanner().length() > 3) {
            adapter.port_banner.setVisibility(View.VISIBLE);
            adapter.port_banner.setText(port.getBanner());
        } else {
            adapter.port_banner.setVisibility(View.GONE);
        }

        String num = port.getPortNumber();
        adapter.port_icon.setOnClickListener(null);
        if (num.equals("80") || num.equals("443") || num.equals("8080") || num.equals("8088") || num.equals("8000")) {
            adapter.port_icon.setIconResource(R.drawable.web);
            adapter.port_icon.setVisibility(View.VISIBLE);
            adapter.port_icon.setOnClickListener(view -> {
                String scheme = num.equals("443") ? "https" : "http";
                String url = scheme + "://" + device.getIp() + (num.equals("80") || num.equals("443") ? "" : ":" + num);
                openlink(url);
            });
        } else if (num.equals("21") || num.equals("445")) {
            adapter.port_icon.setIconResource(R.drawable.folder);
            adapter.port_icon.setVisibility(View.VISIBLE);
            adapter.port_icon.setOnClickListener(view -> openlink("ftp://" + device.getIp()));
        } else if (num.equals("22") || num.equals("23")) {
            adapter.port_icon.setIconResource(R.drawable.terminal);
            adapter.port_icon.setVisibility(View.VISIBLE);
            adapter.port_icon.setOnClickListener(view -> openlink((num.equals("22") ? "ssh" : "telnet") + "://" + device.getIp()));
        } else {
            adapter.port_icon.setIconResource(R.drawable.webscan);
            adapter.port_icon.setVisibility(View.VISIBLE);
        }
        adapter.border.setVisibility(position == ports.size() - 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return ports.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView port_num;
        public TextView port_banner;
        public MaterialButton port_icon;
        public View border;

        public ViewHolder(View v) {
            super(v);
            port_num = v.findViewById(R.id.port_num);
            port_banner = v.findViewById(R.id.port_banner);
            port_icon = v.findViewById(R.id.port_icon);
            border = v.findViewById(R.id.border);
        }
    }

    public void openlink(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.open_link_error, Toast.LENGTH_SHORT).show();
        }
    }
}
