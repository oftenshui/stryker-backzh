package com.zalexdev.stryker.settings.commands;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.UsbDev;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Objects;

public class CommandsAdapter extends RecyclerView.Adapter<CommandsAdapter.ViewHolder> {

    public ArrayList<UsbDev> devices;
    public Context context;
    public Activity activity;
    public Core core;

    public CommandsAdapter(Context context2, Activity mActivity, ArrayList<UsbDev> usb) {
        context = context2;
        devices = usb;
        activity = mActivity;
        core = new Core(context2);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.addusb_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") final int position) {
        holder.name.setText(devices.get(position).getIfc() + " (" + devices.get(position).getPid() + ")");
        holder.command.setText(devices.get(position).getCommandMon());
        holder.delete.setOnClickListener(v -> {
            core.monitorManager.removeDevice(devices.get(position));
            devices.remove(position);
            notifyItemRemoved(position);
        });
        holder.card.setOnClickListener(v -> showEditDialog(position));
        if (devices.get(position).getPid().contains("wlan0")) {
            holder.delete.setVisibility(View.INVISIBLE);
        } else {
            holder.delete.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView command;
        public MaterialCardView card;
        public ImageView delete;

        public ViewHolder(View v) {
            super(v);
            command = v.findViewById(R.id.usb_cmd);
            name = v.findViewById(R.id.usb_name);
            delete = v.findViewById(R.id.delete_usb);
            card = v.findViewById(R.id.usb_card);
        }
    }

    private void showEditDialog(int id) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.addusb_dialog);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AutoCompleteTextView ifc = dialog.findViewById(R.id.select_interface);
        AutoCompleteTextView command = dialog.findViewById(R.id.enable_command);
        AutoCompleteTextView command2 = dialog.findViewById(R.id.disable_command);
        AutoCompleteTextView pid = dialog.findViewById(R.id.device);
        TextView title = dialog.findViewById(R.id.title);
        title.setText(context.getString(R.string.cmd_dialog_edit_title) + " — " + devices.get(id).getPid());

        ifc.setText(devices.get(id).getIfc());
        if (devices.get(id).getIfc().contains("wlan0")) {
            ifc.setEnabled(false);
        } else {
            new Thread(() -> {
                ArrayList<String> interfaces = core.getInterfacesList();
                activity.runOnUiThread(() -> ifc.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, interfaces)));
            }).start();
        }
        command.setText(devices.get(id).commandMon);
        command2.setText(devices.get(id).commandDis);
        pid.setText(devices.get(id).getPid());

        command.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, CustomCommandsWifi.defaultEnableCommands()));
        command2.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, CustomCommandsWifi.defaultDisableCommands()));

        MaterialButton save = dialog.findViewById(R.id.add_button);
        MaterialButton cancel = dialog.findViewById(R.id.cancel_button);
        save.setText(context.getString(R.string.cmd_save));
        save.setIcon(context.getDrawable(R.drawable.save));
        cancel.setOnClickListener(v1 -> dialog.dismiss());
        save.setOnClickListener(v12 -> {
            String ifcText = ifc.getText().toString();
            String cmdText = command.getText().toString();
            String cmd2Text = command2.getText().toString();
            if (ifcText.length() > 0 && cmdText.length() > 0 && cmd2Text.length() > 0) {
                core.monitorManager.changeDevice(new UsbDev(ifcText, pid.getText().toString(), cmdText, cmd2Text), id);
                devices = core.monitorManager.getDevices();
                notifyDataSetChanged();
                dialog.dismiss();
            } else {
                core.toaster(context.getString(R.string.cmd_error_fill_all));
            }
        });
        dialog.show();
    }
}
