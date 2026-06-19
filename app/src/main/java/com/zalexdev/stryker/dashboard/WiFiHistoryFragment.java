package com.zalexdev.stryker.dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Objects;

public class WiFiHistoryFragment extends Fragment {

    private Activity activity;
    private Context context;
    private Core core;
    private RecyclerView mRecyclerView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.storedwifi_fragment, container, false);
        activity = getActivity();
        context = getContext();
        core = new Core(context);
        mRecyclerView = view.findViewById(R.id.stored_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(new StoredAdapter(context, activity, core.getSavedNetworks()));
        ImageView export = view.findViewById(R.id.export);
        export.setOnClickListener(v -> exportNetworks());
        ImageView importer = view.findViewById(R.id.importer);
        importer.setOnClickListener(v -> importNetworks());
        return view;
    }

    private void exportNetworks() {
        try {
            ArrayList<ArrayList<String>> networks = core.getSavedNetworks();
            StringBuilder builder = new StringBuilder();
            builder.append("SSID").append(",").append("Password").append(",").append("WPS Pin").append(",").append("BSSID").append("\n");
            for (ArrayList<String> network : networks) {
                builder.append(network.get(2)).append(",").append(network.get(0)).append(",").append(network.get(1)).append(",").append(network.get(3)).append("\n");
            }
            try {
                @SuppressLint("SdCardPath") File file = new File("/sdcard/Stryker/saved_networks.csv");
                FileWriter writer = new FileWriter(file);
                writer.append(builder.toString());
                writer.flush();
                writer.close();
                core.toaster("Saved networks exported to /sdcard/Stryker/saved_networks.csv");
            } catch (Exception e) {
                e.printStackTrace();
                if (e.getMessage().contains("EPERM")) {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle("Permission denied")
                            .setMessage("Failed to write file, use write as root?")
                            .setPositiveButton(android.R.string.yes, (di, i) -> {
                                String[] lines = builder.toString().split("\n");
                                for (String line : lines) {
                                    core.customCommand("echo '" + line.replace("'", "\\'") + "' > /sdcard/Stryker/saved_networks.csv", true);
                                }
                                core.toaster("Saved networks exported to /sdcard/Stryker/saved_networks.csv");
                            })
                            .setNegativeButton(android.R.string.no, (di, i) -> {
                            })
                            .show();
                } else {
                    core.toaster("Failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            core.toaster("Failed: " + e.getMessage());
        }
    }

    @SuppressLint({"SdCardPath", "SetTextI18n"})
    private void importNetworks() {
        final Dialog valuedialog = new Dialog(context);
        valuedialog.setContentView(R.layout.input_dialog);
        valuedialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        valuedialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView title = valuedialog.findViewById(R.id.title);
        TextInputEditText value = valuedialog.findViewById(R.id.value);
        TextInputLayout valueLayout = valuedialog.findViewById(R.id.value_layout);
        MaterialButton ok = valuedialog.findViewById(R.id.ok);
        MaterialButton cancel = valuedialog.findViewById(R.id.cancel);
        cancel.setOnClickListener(view12 -> valuedialog.dismiss());
        title.setText("Enter path to file");
        valueLayout.setHint("Path");
        value.setText("/sdcard/Stryker/saved_networks.csv");
        ok.setOnClickListener(view1 -> {
            String path = Objects.requireNonNull(value.getText()).toString();
            if (path.isEmpty()) {
                valueLayout.setError("Please enter a path");
            } else if (!core.checkFile(path)) {
                valueLayout.setError("File does not exist");
            } else if (!path.endsWith(".csv")) {
                valueLayout.setError("File must be a csv");
            } else if (!Core.contains(core.customCommand("cat " + path), "SSID,")) {
                valueLayout.setError("File is not a valid Stryker network file");
            } else {
                try {
                    valueLayout.setError(null);
                    ArrayList<String> networks = core.customCommand("cat " + path);
                    networks.remove(0);
                    for (String network : networks) {
                        String[] split = network.split(",");
                        core.saveNetwork(split[3], split[1], split[2], split[0]);
                        core.connectWiFi2(split[0], split[1]);
                    }
                    mRecyclerView.setAdapter(new StoredAdapter(context, activity, core.getSavedNetworks()));
                    core.toaster("Imported networks");
                    valuedialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                    core.toaster("Failed to import networks");
                    valuedialog.dismiss();
                }
            }
        });
        valuedialog.show();
    }
}