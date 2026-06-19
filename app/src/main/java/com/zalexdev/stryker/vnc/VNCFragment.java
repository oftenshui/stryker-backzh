package com.zalexdev.stryker.vnc;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.AdvancedProcess;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.SimpleProcess;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class VNCFragment extends Fragment {

    private Activity activity;
    private Context context;
    private BroadcastReceiver mBroadcastReceiver;
    private Core core;

    private TextView installed;
    private ImageView statusIcon;
    private ProgressBar statusSpinner;
    private TextView stateChip;
    private LinearProgressIndicator progress;
    private TextView textProgress;

    private TextView sectionConnection;
    private MaterialCardView connectionCard;
    private TextView hostValue;
    private TextView portValue;
    private TextView passwordValue;
    private LinearLayout copyHostRow;
    private LinearLayout copyPortRow;
    private LinearLayout copyPasswordRow;
    private ImageView togglePasswordIcon;
    private boolean passwordVisible = false;

    private TextView sectionSettings;
    private TextInputLayout resolutionLayout;
    private AutoCompleteTextView resolution;
    private TextInputLayout portLayout;
    private TextInputEditText port;
    private TextInputLayout passwdLayout;

    private MaterialButton install;
    private MaterialButton toggle;
    private MaterialButton uninstall;
    private MaterialButton changePasswd;

    private AdvancedProcess installProcess;
    private AdvancedProcess uninstallProcess;
    private SimpleProcess changePasswdProcess;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final String PREF_RESOLUTION = "vnc_last_resolution";
    private static final String PREF_PORT = "vnc_last_port";

    public VNCFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vnc, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        installed = view.findViewById(R.id.installed);
        statusIcon = view.findViewById(R.id.vnc_status_icon);
        statusSpinner = view.findViewById(R.id.vnc_status_spinner);
        stateChip = view.findViewById(R.id.vnc_state_chip);
        progress = view.findViewById(R.id.progress);
        textProgress = view.findViewById(R.id.text_prog);

        sectionConnection = view.findViewById(R.id.vnc_section_connection);
        connectionCard = view.findViewById(R.id.vnc_connection_card);
        hostValue = view.findViewById(R.id.vnc_host_value);
        portValue = view.findViewById(R.id.vnc_port_value);
        passwordValue = view.findViewById(R.id.vnc_password_value);
        copyHostRow = view.findViewById(R.id.vnc_copy_host_row);
        copyPortRow = view.findViewById(R.id.vnc_copy_port_row);
        copyPasswordRow = view.findViewById(R.id.vnc_copy_password_row);
        togglePasswordIcon = view.findViewById(R.id.vnc_toggle_password_visible);

        sectionSettings = view.findViewById(R.id.vnc_section_settings);
        resolutionLayout = view.findViewById(R.id.resolution_layout);
        resolution = view.findViewById(R.id.resolution);
        portLayout = view.findViewById(R.id.port_layout);
        port = view.findViewById(R.id.port);
        passwdLayout = view.findViewById(R.id.passwd_layout);

        install = view.findViewById(R.id.install_vnc);
        toggle = view.findViewById(R.id.toggle_vnc);
        uninstall = view.findViewById(R.id.uninstall_vnc);
        changePasswd = view.findViewById(R.id.change_passwd);

        cancelled.set(false);

        wireSettingsDefaults();
        wireConnectionCard();
        wireInstall();
        wireToggle();
        wireUninstall();
        wireChangePassword();

        new Thread(() -> {
            if (!core.checkFolder("/data/local/stryker/release/CORE/VNC"))
                core.customChrootCommand("mkdir /CORE/VNC", true);
        }).start();

        checkVNCInstalled();

        mBroadcastReceiver = new VNCBroadcastReceiver();
        IntentFilter startFilter = new IntentFilter(VNCService.ACTION_START);
        startFilter.addCategory(Intent.CATEGORY_DEFAULT);
        IntentFilter stopFilter = new IntentFilter(VNCService.ACTION_STOP);
        stopFilter.addCategory(Intent.CATEGORY_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(mBroadcastReceiver, startFilter, Context.RECEIVER_NOT_EXPORTED);
            activity.registerReceiver(mBroadcastReceiver, stopFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(mBroadcastReceiver, startFilter);
            activity.registerReceiver(mBroadcastReceiver, stopFilter);
        }
    }

    private void wireSettingsDefaults() {
        ArrayList<String> defaultResolutions = new ArrayList<>();
        defaultResolutions.add(getScreenResolution() + "x24");
        defaultResolutions.add("1920x1080x24");
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, defaultResolutions);
        String savedResolution = core.getString(PREF_RESOLUTION);
        if (savedResolution.isEmpty()) savedResolution = core.getString("previous_vnc_resolution");
        resolution.setAdapter(resAdapter);
        resolution.setText(savedResolution.isEmpty() ? defaultResolutions.get(0) : savedResolution, false);

        String savedPort = core.getString(PREF_PORT);
        if (savedPort.isEmpty()) savedPort = core.getString("previous_vnc_port");
        port.setText(savedPort.isEmpty() ? Integer.toString(5901) : savedPort);

        Objects.requireNonNull(passwdLayout.getEditText()).setText(core.getString("vnc_passwd"));

        resolution.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                core.putString(PREF_RESOLUTION, s == null ? "" : s.toString());
            }
        });
        port.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                core.putString(PREF_PORT, s == null ? "" : s.toString());
            }
        });
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private void wireConnectionCard() {
        copyHostRow.setOnClickListener(v -> copy("VNC host", hostValue.getText().toString()));
        copyPortRow.setOnClickListener(v -> copy("VNC port", portValue.getText().toString()));
        copyPasswordRow.setOnClickListener(v -> copy("VNC password", core.getString("vnc_passwd")));
        togglePasswordIcon.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            renderConnectionCard();
        });
    }

    private void renderConnectionCard() {
        hostValue.setText("localhost");
        String portText = port.getText() == null ? "5901" : port.getText().toString();
        portValue.setText(portText);
        String pwd = core.getString("vnc_passwd");
        passwordValue.setText(passwordVisible || pwd.isEmpty()
                ? (pwd.isEmpty() ? "(empty)" : pwd)
                : "••••••");
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(getContext(), label + " copied", Toast.LENGTH_SHORT).show();
    }

    private void wireInstall() {
        install.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle("VNC installer")
                .setMessage("This will install XFCE + x11vnc into the chroot. ~600 MB download.")
                .setPositiveButton(android.R.string.ok, (di, i) -> new Thread(() -> {
                    core.customCommand("cp /data/data/com.zalexdev.stryker/files/install_xfce.sh /data/local/stryker/release/CORE/VNC/install.sh");
                    core.customCommand("cp /data/data/com.zalexdev.stryker/files/uninstall_xfce.sh /data/local/stryker/release/CORE/VNC/uninstall.sh");
                    core.customCommand("dos2unix /data/local/stryker/release/CORE/VNC/install.sh");
                    core.customCommand("dos2unix /data/local/stryker/release/CORE/VNC/uninstall.sh");
                    core.customCommand("chmod 755 /data/local/stryker/release/CORE/VNC/install.sh");
                    core.customCommand("chmod 755 /data/local/stryker/release/CORE/VNC/uninstall.sh");
                    activity.runOnUiThread(this::enterRunningInstallUi);
                    runInstallProcess();
                }).start())
                .show());
    }

    private void enterRunningInstallUi() {
        if (!isSafe()) return;
        install.setEnabled(false);
        textProgress.setText("Starting installation…");
        textProgress.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        setStatePill("INSTALL", 0xFF5E35B1, true);
    }

    private void runInstallProcess() {
        installProcess = new AdvancedProcess(activity, context, "/CORE/VNC/install.sh", true) {
            boolean determinate = false;

            @Override
            public void onFinished(ArrayList<String> outputList) {
                if (!isSafe()) return;
                core.putString("vnc_installed_de", "xfce");
                checkVNCInstalled();
                textProgress.setVisibility(View.GONE);
                textProgress.setText("");
                progress.setVisibility(View.GONE);
                progress.setIndeterminate(false);
                install.setEnabled(true);
                core.deleteFile("/data/local/stryker/release/usr/share/backgrounds/xfce/*.png");
                core.copyFile("/data/data/com.zalexdev.stryker/files/bg1.png", "/data/local/stryker/release/usr/share/backgrounds/xfce/xfce-verticals.png");
                core.copyFile("/data/data/com.zalexdev.stryker/files/bg3.png", "/data/local/stryker/release/usr/share/backgrounds/xfce/bg3.png");
            }

            @Override
            public void onNewLine(String line) {
                if (!isSafe()) return;
                textProgress.setText(line);
                if (line.contains("fetch")) {
                    textProgress.setText("Fetching packages…");
                }

                if (line.contains("(") && line.contains(")") && line.contains("/")) {
                    String paren = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                    String[] parts = paren.split("/");
                    if (parts.length != 2) return;
                    int progressInt;
                    int progressMax;
                    try {
                        progressInt = Integer.parseInt(parts[0].trim());
                        progressMax = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (!determinate) {
                        determinate = true;
                        progress.setIndeterminate(false);
                        progress.setMax(progressMax);
                    }
                    progress.setProgressCompat(progressInt, true);
                }

                if (line.contains("Failed to update packages") || line.contains("Failed to write")) {
                    showDialog("Install failed", line);
                } else if (line.contains("No previous VNC")) {
                    core.toaster("Default password set to \"stryker\"");
                } else if (line.contains("Use the helper scripts")) {
                    showDialog("Install complete", "VNC server installed.");
                }
            }

            @Override
            public void onEvent(String line) {
            }
        };
    }

    private void wireToggle() {
        toggle.setOnClickListener(v -> {
            if (isVNCStarted()) {
                stopService();
            } else {
                startService(resolution.getText().toString(), port.getText().toString());
            }
        });
    }

    private void wireUninstall() {
        uninstall.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle("Uninstall VNC?")
                .setMessage("Removes XFCE + x11vnc from the chroot. Disk space is freed; you can reinstall later.")
                .setNegativeButton(android.R.string.cancel, (di, i) -> {})
                .setPositiveButton("Uninstall", (di, i) -> {
                    freezeActivity();
                    textProgress.setText("Starting uninstallation…");
                    textProgress.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    progress.setIndeterminate(true);
                    uninstallProcess = new AdvancedProcess(activity, context, "/CORE/VNC/uninstall.sh", true) {
                        @Override
                        public void onFinished(ArrayList<String> outputList) {
                            if (!isSafe()) return;
                            if (!isVNCInstalled()) {
                                core.remove("vnc_installed_de");
                                core.toaster("VNC server uninstalled.");
                                vncNotInstalled();
                                unfreezeActivity();
                            }
                            textProgress.setVisibility(View.GONE);
                            textProgress.setText("");
                            progress.setVisibility(View.GONE);
                            progress.setIndeterminate(false);
                        }

                        @Override
                        public void onNewLine(String line) {
                            if (!isSafe()) return;
                            textProgress.setText(line);
                            if (line.contains("Error")) {
                                showDialog("Uninstall failed", line);
                            } else if (line.contains("Failed")) {
                                showDialog("Uninstall warning", line);
                            }
                        }

                        @Override
                        public void onEvent(String line) {
                        }
                    };
                })
                .show());
    }

    private void wireChangePassword() {
        changePasswd.setOnClickListener(v -> {
            changePasswd.setEnabled(false);
            String newPwd = passwdLayout.getEditText() != null
                    ? passwdLayout.getEditText().getText().toString() : "";
            changePasswdProcess = new SimpleProcess(activity, "x11vnc -storepasswd " + newPwd + " /root/.vnc/passwd", true) {
                @Override
                public void onFinished(ArrayList<String> outputList) {
                    if (!isSafe()) return;
                    if (Core.contains(outputList, "stored")) {
                        core.toaster("Password changed successfully!");
                        core.putString("vnc_passwd", newPwd);
                        renderConnectionCard();
                    } else {
                        core.toaster("Error when changing password.");
                    }
                    changePasswd.setEnabled(true);
                }
            };
        });
    }

    private String getScreenResolution() {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);
        int width = point.x;
        int height = point.y;
        return height + "x" + width;
    }

    private boolean isVNCInstalled() {
        return Core.contains(core.customChrootCommand("which x11vnc"), "x11vnc");
    }

    private void vncInstalled() {
        install.setVisibility(View.GONE);
        resolutionLayout.setVisibility(View.VISIBLE);
        portLayout.setVisibility(View.VISIBLE);
        toggle.setVisibility(View.VISIBLE);
        uninstall.setVisibility(View.VISIBLE);
        changePasswd.setVisibility(View.VISIBLE);
        passwdLayout.setVisibility(View.VISIBLE);
        sectionConnection.setVisibility(View.VISIBLE);
        connectionCard.setVisibility(View.VISIBLE);
        sectionSettings.setVisibility(View.VISIBLE);
        renderConnectionCard();
    }

    private void vncNotInstalled() {
        install.setVisibility(View.VISIBLE);
        resolutionLayout.setVisibility(View.GONE);
        portLayout.setVisibility(View.GONE);
        toggle.setVisibility(View.GONE);
        uninstall.setVisibility(View.GONE);
        changePasswd.setVisibility(View.GONE);
        passwdLayout.setVisibility(View.GONE);
        sectionConnection.setVisibility(View.GONE);
        connectionCard.setVisibility(View.GONE);
        sectionSettings.setVisibility(View.GONE);
        installed.setText("Not installed — tap install to fetch ~600 MB.");
        setStatePill("OFF", 0xFF757575, false);
    }

    private void checkVNCInstalled() {
        new Thread(() -> {
            if (isVNCInstalled()) {
                if (isSafe()) activity.runOnUiThread(() -> {
                    if (!isSafe()) return;
                    vncInstalled();
                    installed.setText("Installed: " + core.getString("vnc_installed_de").toUpperCase()
                            + " · chroot ready");
                });
                checkVNCStarted();
            } else {
                if (isSafe()) activity.runOnUiThread(() -> {
                    if (!isSafe()) return;
                    vncNotInstalled();
                });
            }
        }).start();
    }

    private boolean isVNCStarted() {
        return !core.customChrootCommand("pidof Xvfb").isEmpty();
    }

    private void vncStarted() {
        resolutionLayout.setEnabled(false);
        portLayout.setEnabled(false);
        toggle.setText("Stop VNC server");
        toggle.setIconResource(R.drawable.stop);
        uninstall.setEnabled(false);
        changePasswd.setEnabled(false);
        passwdLayout.setEnabled(false);
        setStatePill("RUN", 0xFF2E7D32, false);
        installed.setText("Running · accept VNC connections on the port below");
    }

    private void vncNotStarted() {
        resolutionLayout.setEnabled(true);
        portLayout.setEnabled(true);
        toggle.setText("Start VNC server");
        toggle.setIconResource(R.drawable.run);
        uninstall.setEnabled(true);
        changePasswd.setEnabled(true);
        passwdLayout.setEnabled(true);
        setStatePill("OFF", 0xFFF9A825, false);
        installed.setText("Stopped · tap Start to spin up xvfb + x11vnc");
    }

    private void setStatePill(String text, int color, boolean spinning) {
        if (stateChip != null) {
            stateChip.setText(text);
            stateChip.setTextColor(color);
            if (stateChip.getBackground() != null) {
                stateChip.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                stateChip.getBackground().setAlpha(40);
            }
        }
        if (statusSpinner != null) {
            statusSpinner.setVisibility(spinning ? View.VISIBLE : View.GONE);
        }
        if (statusIcon != null) {
            statusIcon.setVisibility(spinning ? View.GONE : View.VISIBLE);
            statusIcon.setColorFilter(color);
        }
    }

    private void checkVNCStarted() {
        new Thread(() -> {
            if (isVNCStarted()) {
                if (isSafe()) activity.runOnUiThread(() -> {
                    if (isSafe()) vncStarted();
                });
            } else {
                if (isSafe()) activity.runOnUiThread(() -> {
                    if (isSafe()) vncNotStarted();
                });
            }
        }).start();
    }

    private void freezeActivity() {
        resolutionLayout.setEnabled(false);
        portLayout.setEnabled(false);
        toggle.setEnabled(false);
        uninstall.setEnabled(false);
        changePasswd.setEnabled(false);
        passwdLayout.setEnabled(false);
    }

    private void unfreezeActivity() {
        resolutionLayout.setEnabled(true);
        portLayout.setEnabled(true);
        toggle.setEnabled(true);
        uninstall.setEnabled(true);
        changePasswd.setEnabled(true);
        passwdLayout.setEnabled(true);
    }

    private boolean isSafe() {
        return !cancelled.get() && activity != null && isAdded();
    }

    private void showDialog(String title, String message) {
        if (!isSafe()) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (di, i) -> {})
                .setCancelable(true)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        new Thread(() -> {
            if (isVNCInstalled() && activity != null && isAdded()) {
                activity.runOnUiThread(this::checkVNCStarted);
            }
        }, "vnc-resume-check").start();
    }

    public void startService(String resolution, String port) {
        Intent serviceIntent = new Intent(context, VNCService.class);
        serviceIntent.putExtra(VNCService.EXTRA_RESOLUTION, resolution);
        serviceIntent.putExtra(VNCService.EXTRA_PORT, port);
        serviceIntent.setAction(VNCService.ACTION_START);
        ContextCompat.startForegroundService(context, serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(context, VNCService.class);
        serviceIntent.setAction(VNCService.ACTION_STOP);
        ContextCompat.startForegroundService(context, serviceIntent);
    }

    @Override
    public void onDestroyView() {
        cancelled.set(true);
        if (installProcess != null) {
            installProcess.kill();
            installProcess = null;
        }
        if (uninstallProcess != null) {
            uninstallProcess.kill();
            uninstallProcess = null;
        }
        if (changePasswdProcess != null) {
            changePasswdProcess.kill();
            changePasswdProcess = null;
        }
        try {
            if (activity != null) activity.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroyView();
    }

    private class VNCBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isSafe()) return;
            if (Objects.equals(intent.getAction(), VNCService.ACTION_START)) {
                vncStarted();
            } else if (Objects.equals(intent.getAction(), VNCService.ACTION_STOP)) {
                vncNotStarted();
            }
        }
    }
}
