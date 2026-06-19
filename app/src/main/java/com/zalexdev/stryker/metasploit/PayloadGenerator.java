package com.zalexdev.stryker.metasploit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.AdvancedProcess;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;

public final class PayloadGenerator {

    private static final String VENOM_TYPES =
            "windows apk powershell python linux php perl tomcat osx java bash asp aspx";

    private PayloadGenerator() {}

    public static void show(Context context, Activity activity, Core core) {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.msfgenerate_dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        ChipGroup presetGroup = dialog.findViewById(R.id.payload_preset_group);
        TextInputLayout typeLayout = dialog.findViewById(R.id.payload_type_layout);
        MaterialAutoCompleteTextView typeField = dialog.findViewById(R.id.type_payload);
        TextInputEditText ipField = dialog.findViewById(R.id.ip_payload);
        TextInputEditText portField = dialog.findViewById(R.id.port_payload);
        TextInputEditText nameField = dialog.findViewById(R.id.filename_payload);
        MaterialButton generate = dialog.findViewById(R.id.launch_button);
        MaterialButton cancel = dialog.findViewById(R.id.cancel_payload);
        LinearProgressIndicator progress = dialog.findViewById(R.id.progress_payload);
        ShimmerFrameLayout shimmer = dialog.findViewById(R.id.msf_shim);
        TextView status = dialog.findViewById(R.id.payload_status);
        TextView log = dialog.findViewById(R.id.found_hydra);
        View logContainer = dialog.findViewById(R.id.payload_log_container);

        typeField.setSimpleItems(VENOM_TYPES.split(" "));
        typeField.setText("windows", false);
        ipField.setText(core.getLocalIpaddress());
        portField.setText("4444");
        nameField.setText("payload");

        presetGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String type;
            boolean showCustom = false;
            if (id == R.id.payload_preset_android) type = "apk";
            else if (id == R.id.payload_preset_linux) type = "linux";
            else if (id == R.id.payload_preset_php) type = "php";
            else if (id == R.id.payload_preset_python) type = "python";
            else if (id == R.id.payload_preset_custom) { type = ""; showCustom = true; }
            else type = "windows";
            typeField.setText(type, false);
            typeLayout.setVisibility(showCustom ? View.VISIBLE : View.GONE);
        });
        ((Chip) presetGroup.findViewById(R.id.payload_preset_windows)).setChecked(true);

        final AdvancedProcess[] msfpc = {null};

        generate.setOnClickListener(view -> {
            String type = typeField.getText().toString().trim();
            String ip = ipField.getText() == null ? "" : ipField.getText().toString().trim();
            String port = portField.getText() == null ? "" : portField.getText().toString().trim();
            String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
            if (type.length() < 2 || port.length() < 1 || ip.length() < 7 || name.length() < 1) {
                Toast.makeText(context, R.string.payload_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            shimmer.showShimmer(true);
            progress.setVisibility(View.VISIBLE);
            logContainer.setVisibility(View.VISIBLE);
            log.setText("");
            status.setText(R.string.payload_status_running);
            generate.setEnabled(false);

            String cmd = "msfpc " + type + " " + ip + " " + port;
            msfpc[0] = new AdvancedProcess(activity, context, cmd, true) {
                @Override
                public void onFinished(ArrayList<String> outputList) {
                    activity.runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        shimmer.hideShimmer();
                        generate.setEnabled(true);
                        boolean ok = false;
                        for (String s : outputList) if (s != null && s.contains("created:")) { ok = true; break; }
                        status.setText(ok ? R.string.payload_status_done : R.string.payload_status_failed);
                    });
                }

                @Override
                public void onNewLine(String line) {
                    if (line == null) return;
                    activity.runOnUiThread(() -> log.append(line + "\n"));
                    if (line.contains("created:")) {
                        final String created = stripAnsi(line);
                        final java.util.regex.Matcher m =
                                java.util.regex.Pattern.compile("created:\\s*'([^']+)'").matcher(created);
                        if (!m.find()) return;
                        new Thread(() -> {
                            core.createFolder("/sdcard/Stryker/payloads");
                            String srcPath = m.group(1).replaceAll("/+", "/");
                            String baseName = srcPath.substring(srcPath.lastIndexOf('/') + 1);
                            int dot = baseName.lastIndexOf('.');
                            String ext = dot >= 0 ? baseName.substring(dot) : "";
                            String fileName = name + ext;
                            core.customChrootCommand(
                                    "cp '" + srcPath + "' '/sdcard/Stryker/payloads/" + fileName + "'",
                                    true);
                        }).start();
                    }
                }

                private String stripAnsi(String s) {
                    return s.replaceAll("\\[[0-9;]*[a-zA-Z]", "")
                            .replaceAll("\\[[0-9;]*m", "");
                }

                @Override
                public void onEvent(String line) {
                }
            };
        });
        cancel.setOnClickListener(view -> {
            dialog.dismiss();
            if (msfpc[0] != null) msfpc[0].kill();
        });

        dialog.show();
    }
}
