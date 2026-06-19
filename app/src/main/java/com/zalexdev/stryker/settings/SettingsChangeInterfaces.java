package com.zalexdev.stryker.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Objects;

public class SettingsChangeInterfaces extends Fragment {

    private Context context;
    private Core core;
    private TextView scanInterface;
    private TextView handshakeInterface;
    private TextView deauthInterface;
    private TextView wpsInterface;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_wifi, container, false);
        context = getContext();
        core = new Core(context);
        LinearLayout scanInterfaceLayout = view.findViewById(R.id.scan_interface_view);
        LinearLayout handshakeInterfaceLayout = view.findViewById(R.id.hs_interface_view);
        LinearLayout deauthInterfaceLayout = view.findViewById(R.id.deauth_interface_view);
        LinearLayout wpsInterfaceLayout = view.findViewById(R.id.wps_interface_view);
        scanInterface = view.findViewById(R.id.scan_interface);
        handshakeInterface = view.findViewById(R.id.hs_interface);
        deauthInterface = view.findViewById(R.id.deauth_interface);
        wpsInterface = view.findViewById(R.id.wps_interface);
        scanInterfaceLayout.setOnClickListener(v -> changeInterface("wlan_wifi"));
        handshakeInterfaceLayout.setOnClickListener(v -> changeInterface("wlan_scan"));
        deauthInterfaceLayout.setOnClickListener(v -> changeInterface("wlan_deauth"));
        wpsInterfaceLayout.setOnClickListener(v -> changeInterface("wlan_wps"));
        refreshLabels();
        return view;
    }

    private void refreshLabels() {
        scanInterface.setText(displayValue(core.getString("wlan_wifi")));
        handshakeInterface.setText(displayValue(core.getString("wlan_scan")));
        deauthInterface.setText(displayValue(core.getString("wlan_deauth")));
        wpsInterface.setText(displayValue(core.getString("wlan_wps")));
    }

    private String displayValue(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }

    private void changeInterface(String pref) {
        new Thread(() -> {
            ArrayList<String> interfaces = core.getInterfacesList();
            Activity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> showInterfacePicker(pref, interfaces));
        }).start();
    }

    private void showInterfacePicker(String pref, ArrayList<String> interfaces) {
        String[] items = new String[interfaces.size() + 1];
        for (int i = 0; i < interfaces.size(); i++) {
            items[i] = interfaces.get(i);
        }
        items[items.length - 1] = context.getResources().getString(R.string.customvalue);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pick)
                .setItems(items, (dialogInterface, i) -> {
                    if (i != items.length - 1) {
                        core.putString(pref, items[i]);
                        refreshLabels();
                    } else {
                        promptCustomValue(pref);
                    }
                })
                .show();
    }

    private void promptCustomValue(String pref) {
        final Dialog valueDialog = new Dialog(context);
        valueDialog.setContentView(R.layout.input_dialog);
        Objects.requireNonNull(valueDialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        valueDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView title = valueDialog.findViewById(R.id.title);
        TextInputEditText value = valueDialog.findViewById(R.id.value);
        MaterialButton ok = valueDialog.findViewById(R.id.ok);
        MaterialButton cancel = valueDialog.findViewById(R.id.cancel);

        title.setText(context.getResources().getString(R.string.customvalue));
        cancel.setOnClickListener(v -> valueDialog.dismiss());
        ok.setOnClickListener(v -> {
            String entered = Objects.requireNonNull(value.getText()).toString().trim();
            if (entered.isEmpty()) {
                value.setError(context.getResources().getString(R.string.customvalue));
                return;
            }
            core.putString(pref, entered);
            valueDialog.dismiss();
            refreshLabels();
        });
        valueDialog.show();
    }
}
