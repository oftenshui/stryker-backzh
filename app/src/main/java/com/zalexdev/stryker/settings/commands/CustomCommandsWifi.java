package com.zalexdev.stryker.settings.commands;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.UsbDev;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomCommandsWifi extends Fragment {

    public Core core;
    public Context context;
    public Activity activity;

    private CommandsAdapter adapter;
    private final AtomicBoolean alive = new AtomicBoolean(true);

    public CustomCommandsWifi() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.addusb_fragment, container, false);
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        alive.set(true);

        RecyclerView mRecyclerView = view.findViewById(R.id.custom_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        mRecyclerView.setItemViewCacheSize(255);

        MaterialButton add = view.findViewById(R.id.add_usb);
        adapter = new CommandsAdapter(context, activity, core.monitorManager.getDevices());
        mRecyclerView.setAdapter(adapter);

        add.setOnClickListener(v -> showAddDialog(adapter));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (adapter != null && core != null) {
            adapter.devices = core.monitorManager.getDevices();
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        alive.set(false);
        super.onDestroyView();
    }

    private void showAddDialog(CommandsAdapter adapter) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.addusb_dialog);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AutoCompleteTextView ifc = dialog.findViewById(R.id.select_interface);
        AutoCompleteTextView command = dialog.findViewById(R.id.enable_command);
        AutoCompleteTextView command2 = dialog.findViewById(R.id.disable_command);
        AutoCompleteTextView pid = dialog.findViewById(R.id.device);

        if (core.monitorManager.getPid() != null && core.monitorManager.getPid().length() > 0) {
            pid.setText(core.monitorManager.getPid());
        } else {
            pid.setEnabled(false);
            pid.setText("none");
        }

        new Thread(() -> {
            ArrayList<String> interfaces = core.getInterfacesList();
            if (!alive.get() || activity == null || !isAdded()) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!alive.get() || activity == null || !isAdded()) {
                    return;
                }
                ifc.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, interfaces));
            });
        }).start();

        command.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, defaultEnableCommands()));
        command2.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, defaultDisableCommands()));

        MaterialButton save = dialog.findViewById(R.id.add_button);
        MaterialButton cancel = dialog.findViewById(R.id.cancel_button);
        cancel.setOnClickListener(v1 -> dialog.dismiss());
        save.setOnClickListener(v12 -> {
            String ifcText = ifc.getText().toString();
            String cmdText = command.getText().toString();
            String cmd2Text = command2.getText().toString();
            if (ifcText.length() > 0 && cmdText.length() > 0 && cmd2Text.length() > 0 && !ifcText.contains("wlan0")) {
                core.monitorManager.addDevice(new UsbDev(ifcText, pid.getText().toString(), cmdText, cmd2Text));
                adapter.devices = core.monitorManager.getDevices();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            } else if (ifcText.contains("wlan0")) {
                ifc.setError(getString(R.string.cmd_error_no_wlan0));
            } else {
                ifc.setError(getString(R.string.cmd_error_fill_all));
            }
        });
        dialog.show();
    }

    static ArrayList<String> defaultEnableCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("ip link set $ifc down;echo '4' > /sys/module/wlan/parameters/con_mode;ip link set $ifc up");
        commands.add("ip link set $ifc down;iw dev $ifc set type monitor;ip link set $ifc up");
        commands.add("airmon-ng start $ifc $ch");
        return commands;
    }

    static ArrayList<String> defaultDisableCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("ip link set $ifc down;echo '0' > /sys/module/wlan/parameters/con_mode;ip link set $ifc up;svc wifi enable");
        commands.add("ip link set $ifc down;iw dev $ifc set type managed;ip link set $ifc up");
        commands.add("airmon-ng stop $ifc");
        return commands;
    }
}
