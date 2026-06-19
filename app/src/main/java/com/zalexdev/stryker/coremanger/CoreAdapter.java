package com.zalexdev.stryker.coremanger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Package;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;

public class CoreAdapter extends RecyclerView.Adapter<CoreAdapter.ViewHolder> {

    private final Activity activity;
    private final Context context;
    private final Core core;
    private final ArrayList<Package> packages;
    private final Runnable onChange;

    public CoreAdapter(Context context, Activity activity, ArrayList<Package> packages, Runnable onChange) {
        this.context = context;
        this.packages = packages;
        this.activity = activity;
        this.onChange = onChange;
        core = new Core(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.package_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, @SuppressLint("RecyclerView") int position) {
        Package temp = packages.get(position);
        h.title.setText(temp.getName());
        h.version.setText(temp.getVersion());
        h.progress.setVisibility(View.GONE);
        h.action.setVisibility(View.VISIBLE);

        if (temp.isPythonPackage()) {
            h.sourceChip.setText(R.string.core_mgr_chip_python);
            h.sourceChip.setTextColor(android.graphics.Color.parseColor("#FB8C00"));
        } else {
            h.sourceChip.setText(R.string.core_mgr_chip_apk);
            h.sourceChip.setTextColor(android.graphics.Color.parseColor("#1E88E5"));
        }

        if (temp.isInstalled()) {
            h.installedChip.setVisibility(View.VISIBLE);
            h.action.setImageResource(R.drawable.delete);
            h.action.setColorFilter(android.graphics.Color.parseColor("#D32F2F"));
            h.action.setOnClickListener(v -> uninstall(h, temp));
        } else {
            h.installedChip.setVisibility(View.GONE);
            h.action.setImageResource(R.drawable.download);
            h.action.setColorFilter(android.graphics.Color.parseColor("#AB47BC"));
            h.action.setOnClickListener(v -> install(h, temp));
        }
    }

    private void install(ViewHolder h, Package temp) {
        h.action.setVisibility(View.GONE);
        h.progress.setVisibility(View.VISIBLE);
        core.toaster(activity, context.getString(R.string.core_mgr_installing, temp.getName()));
        new Thread(() -> {
            boolean ok;
            if (temp.isPythonPackage()) {
                ok = Core.contains(core.customChrootCommand("pip install --break-system-packages " + temp.getName()), "Successfully installed");
            } else {
                ok = Core.contains(core.customChrootCommand("apk add " + temp.getName()), "OK:");
            }
            final boolean okFinal = ok;
            activity.runOnUiThread(() -> {
                h.progress.setVisibility(View.GONE);
                h.action.setVisibility(View.VISIBLE);
                if (okFinal) {
                    core.toaster(context.getString(R.string.core_mgr_install_ok, temp.getName()));
                    temp.setInstalled(true);
                    notifyItemChanged(h.getAdapterPosition());
                    if (onChange != null) onChange.run();
                } else {
                    core.toaster(context.getString(R.string.core_mgr_install_failed, temp.getName()));
                }
            });
        }).start();
    }

    private void uninstall(ViewHolder h, Package temp) {
        h.action.setVisibility(View.GONE);
        h.progress.setVisibility(View.VISIBLE);
        core.toaster(activity, context.getString(R.string.core_mgr_uninstalling, temp.getName()));
        new Thread(() -> {
            boolean ok;
            if (temp.isPythonPackage()) {
                ok = Core.contains(core.customChrootCommand("pip uninstall " + temp.getName() + " -y"), "Successfully uninstalled");
            } else {
                ok = Core.contains(core.customChrootCommand("apk del " + temp.getName()), "OK:");
            }
            final boolean okFinal = ok;
            activity.runOnUiThread(() -> {
                h.progress.setVisibility(View.GONE);
                h.action.setVisibility(View.VISIBLE);
                if (okFinal) {
                    core.toaster(context.getString(R.string.core_mgr_uninstall_ok, temp.getName()));
                    temp.setInstalled(false);
                    notifyItemChanged(h.getAdapterPosition());
                    if (onChange != null) onChange.run();
                } else {
                    core.toaster(context.getString(R.string.core_mgr_uninstall_failed, temp.getName()));
                }
            });
        }).start();
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    @Override
    public long getItemId(int position) {
        return packages.get(position).getName().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView version;
        public TextView sourceChip;
        public TextView installedChip;
        public ImageView action;
        public CircularProgressIndicator progress;

        public ViewHolder(View v) {
            super(v);
            version = v.findViewById(R.id.version);
            title = v.findViewById(R.id.title);
            sourceChip = v.findViewById(R.id.pkg_source_chip);
            installedChip = v.findViewById(R.id.pkg_installed_chip);
            action = v.findViewById(R.id.run_sploit);
            progress = v.findViewById(R.id.pkg_progress);
        }
    }
}
