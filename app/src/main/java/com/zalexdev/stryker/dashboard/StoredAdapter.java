package com.zalexdev.stryker.dashboard;

import android.annotation.SuppressLint;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;

public class StoredAdapter extends RecyclerView.Adapter<StoredAdapter.ViewHolder> {

    private final Activity activity;
    private final Context context;
    private final Core core;
    private final ArrayList<ArrayList<String>> stored;

    public StoredAdapter(Context context, Activity activity, ArrayList<ArrayList<String>> stored) {
        this.activity = activity;
        this.context = context;
        core = new Core(context);
        this.stored = stored;
        for (int i = 0; i < stored.size(); i++) {
            if (stored.get(i).size() < 4) {
                stored.remove(i);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.storedwifi_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint({"UseCompatLoadingForDrawables", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder adapter, @SuppressLint("RecyclerView") final int position) {
        adapter.name.setText(stored.get(position).get(2) + " (" + stored.get(position).get(3).toUpperCase() + ")");
        adapter.additional.setText(getAdditionalInfo(position));
        adapter.copy_password.setOnClickListener(view -> {
            core.copyToClipBoard(stored.get(position).get(0));
            core.toaster("Password copied to clipboard!");
        });
        adapter.copy_password.setOnLongClickListener(view -> {
            core.toaster("Copy password to clipboard.");
            return false;
        });
        adapter.delete.setOnClickListener(view -> removeNetwork(position));
        adapter.cardView.setOnLongClickListener(view -> {
            removeNetwork(position);
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return stored.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private String getAdditionalInfo(int position) {
        StringBuilder result = new StringBuilder();
        String password = stored.get(position).get(0);
        String wps_pin = stored.get(position).get(1);
        if (!password.isEmpty()) {
            result.append("Password: ").append(password);
        }
        if (!wps_pin.isEmpty()) {
            result.append(!password.isEmpty() ? "\n" : "").append("WPS Pin: ").append(wps_pin);
        }
        return result.toString();
    }

    private void removeNetwork(int position) {
        MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(context)
                .setTitle("Confirmation")
                .setMessage("Are you sure to delete this saved network?")
                .setPositiveButton(context.getResources().getString(R.string.yes), (di, i) -> {
                    core.removeSavedNetwork(stored.get(position).get(3));
                    stored.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, stored.size());
                })
                .setNegativeButton(android.R.string.cancel, (di, i) -> {});
        adb.show();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView name;
        public TextView additional;
        public ImageView delete;
        public ImageView copy_password;
        public MaterialCardView cardView;

        public ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            additional = v.findViewById(R.id.additional);
            delete = v.findViewById(R.id.delete);
            copy_password = v.findViewById(R.id.copy);
            cardView = v.findViewById(R.id.item);
        }
    }
}
