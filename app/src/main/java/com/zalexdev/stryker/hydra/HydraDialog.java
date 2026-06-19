package com.zalexdev.stryker.hydra;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.NoNestedScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HydraDialog {

    private static final String[] HYDRA_SERVICES = new String[] {
            "ssh", "ftp", "ftps", "telnet", "smb", "rdp", "mysql", "mssql",
            "postgres", "vnc", "snmp", "redis", "pop3", "pop3s", "imap",
            "imaps", "smtp", "smtps", "rexec", "rlogin", "cisco", "cisco-enable",
            "ldap2", "ldap2s", "ldap3", "asterisk", "svn", "xmpp"
    };

    private static final Map<String, String> DEFAULT_PORTS = new LinkedHashMap<>();
    static {
        DEFAULT_PORTS.put("ssh", "22");
        DEFAULT_PORTS.put("ftp", "21");
        DEFAULT_PORTS.put("ftps", "990");
        DEFAULT_PORTS.put("telnet", "23");
        DEFAULT_PORTS.put("smb", "445");
        DEFAULT_PORTS.put("rdp", "3389");
        DEFAULT_PORTS.put("mysql", "3306");
        DEFAULT_PORTS.put("mssql", "1433");
        DEFAULT_PORTS.put("postgres", "5432");
        DEFAULT_PORTS.put("vnc", "5900");
        DEFAULT_PORTS.put("snmp", "161");
        DEFAULT_PORTS.put("redis", "6379");
        DEFAULT_PORTS.put("pop3", "110");
        DEFAULT_PORTS.put("imap", "143");
        DEFAULT_PORTS.put("smtp", "25");
    }

    private HydraDialog() {}

    public static void show(Context context, Activity activity, Core core, Device device) {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_hydra);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        ShimmerFrameLayout shimmer = dialog.findViewById(R.id.hydra_dialog_shim);
        TextView status = dialog.findViewById(R.id.hydra_dialog_status);
        ImageView expand = dialog.findViewById(R.id.hydra_dialog_expand);

        ChipGroup serviceGroup = dialog.findViewById(R.id.hydra_service_group);
        TextInputLayout customSvcLayout = dialog.findViewById(R.id.hydra_service_custom_layout);
        MaterialAutoCompleteTextView customSvc = dialog.findViewById(R.id.hydra_service_custom);
        TextInputEditText hostField = dialog.findViewById(R.id.hydra_host);
        TextInputEditText portField = dialog.findViewById(R.id.hydra_port);

        ChipGroup modeGroup = dialog.findViewById(R.id.hydra_mode_group);
        Chip modeWordlists = dialog.findViewById(R.id.hydra_mode_wordlists);
        Chip modeSingle = dialog.findViewById(R.id.hydra_mode_single);
        LinearLayout wlBlock = dialog.findViewById(R.id.hydra_block_wordlists);
        LinearLayout singleBlock = dialog.findViewById(R.id.hydra_block_single);
        MaterialAutoCompleteTextView loginWl = dialog.findViewById(R.id.hydra_login_wl);
        TextInputLayout loginWlLayout = dialog.findViewById(R.id.hydra_login_wl_layout);
        MaterialAutoCompleteTextView passwordWl = dialog.findViewById(R.id.hydra_password_wl);
        TextInputEditText singleLogin = dialog.findViewById(R.id.hydra_single_login);
        TextInputEditText singlePassword = dialog.findViewById(R.id.hydra_single_password);

        Slider threadsSlider = dialog.findViewById(R.id.hydra_threads_slider);
        TextView threadsValue = dialog.findViewById(R.id.hydra_threads_value);
        Chip verboseChip = dialog.findViewById(R.id.hydra_chip_verbose);
        Chip firstChip = dialog.findViewById(R.id.hydra_chip_first);

        LinearProgressIndicator progress = dialog.findViewById(R.id.hydra_dialog_progress);
        MaterialCardView foundCard = dialog.findViewById(R.id.hydra_found_card);
        TextView foundText = dialog.findViewById(R.id.hydra_found_text);
        ImageView foundCopy = dialog.findViewById(R.id.hydra_found_copy);

        LinearLayout terminalContainer = dialog.findViewById(R.id.hydra_terminal_container);
        NoNestedScrollView terminalScroll = dialog.findViewById(R.id.hydra_terminal_scroll);
        TextView terminalText = dialog.findViewById(R.id.hydra_terminal_text);

        MaterialButton launchBtn = dialog.findViewById(R.id.hydra_btn_launch);
        MaterialButton stopBtn = dialog.findViewById(R.id.hydra_btn_stop);
        MaterialButton closeBtn = dialog.findViewById(R.id.hydra_btn_close);

        customSvc.setSimpleItems(HYDRA_SERVICES);
        hostField.setText(device != null ? device.getIp() : "");
        portField.setText(DEFAULT_PORTS.get("ssh"));
        threadsValue.setText("16");
        threadsSlider.addOnChangeListener((slider, value, fromUser) ->
                threadsValue.setText(String.valueOf((int) value)));

        new Thread(() -> {
            ArrayList<String> list = core.getListFiles("/sdcard/Stryker/wordlists");
            if (list == null) list = new ArrayList<>();
            Collections.sort(list);
            String[] arr = list.toArray(new String[0]);
            activity.runOnUiThread(() -> {
                loginWl.setSimpleItems(arr);
                passwordWl.setSimpleItems(arr);
                if (arr.length > 0) {
                    loginWl.setText(arr[0], false);
                    passwordWl.setText(arr[0], false);
                }
            });
        }, "hydra-wordlist-load").start();

        final String[] selectedService = { "ssh" };
        serviceGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            boolean showCustom = false;
            if (id == R.id.hydra_chip_ssh) selectedService[0] = "ssh";
            else if (id == R.id.hydra_chip_ftp) selectedService[0] = "ftp";
            else if (id == R.id.hydra_chip_smb) selectedService[0] = "smb";
            else if (id == R.id.hydra_chip_rdp) selectedService[0] = "rdp";
            else if (id == R.id.hydra_chip_mysql) selectedService[0] = "mysql";
            else if (id == R.id.hydra_chip_postgres) selectedService[0] = "postgres";
            else if (id == R.id.hydra_chip_telnet) selectedService[0] = "telnet";
            else if (id == R.id.hydra_chip_vnc) selectedService[0] = "vnc";
            else if (id == R.id.hydra_chip_custom) {
                selectedService[0] = "";
                showCustom = true;
            }
            customSvcLayout.setVisibility(showCustom ? View.VISIBLE : View.GONE);
            String defPort = DEFAULT_PORTS.get(selectedService[0]);
            if (defPort != null) portField.setText(defPort);
            updateLoginVisibility(selectedService[0], loginWlLayout);
        });

        modeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean wl = checkedIds.contains(R.id.hydra_mode_wordlists);
            wlBlock.setVisibility(wl ? View.VISIBLE : View.GONE);
            singleBlock.setVisibility(wl ? View.GONE : View.VISIBLE);
        });

        final HydraEngine[] engineRef = { null };
        final boolean[] running = { false };

        HydraEngine.Listener listener = new HydraEngine.Listener() {
            @Override
            public void onState(HydraEngine.State state, String reason) {
                switch (state) {
                    case RUNNING:
                        running[0] = true;
                        shimmer.showShimmer(true);
                        progress.setVisibility(View.VISIBLE);
                        progress.setIndeterminate(true);
                        stopBtn.setVisibility(View.VISIBLE);
                        launchBtn.setEnabled(false);
                        launchBtn.setAlpha(0.6f);
                        status.setText(reason);
                        break;
                    case FOUND:
                    case NONE:
                    case ERROR:
                    case KILLED:
                        running[0] = false;
                        shimmer.hideShimmer();
                        progress.setVisibility(View.GONE);
                        stopBtn.setVisibility(View.GONE);
                        launchBtn.setEnabled(true);
                        launchBtn.setAlpha(1.0f);
                        status.setText(reason);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onLine(String line) {
                terminalText.append(line + "\n");
                terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
            }

            @Override
            public void onCredentialFound(HydraEngine.Credential credential) {
                String text = String.format(Locale.US, "%s  →  %s : %s",
                        credential.host, credential.login, credential.password);
                foundText.setText(text);
                foundCard.setVisibility(View.VISIBLE);
                status.setText(context.getString(R.string.hydra_dialog_status_found));
            }

            @Override
            public void onProgress(int tried, int total) {
                if (total > 0) {
                    progress.setIndeterminate(false);
                    progress.setMax(total);
                    progress.setProgress(tried, true);
                    status.setText(context.getString(R.string.hydra_dialog_status_progress, tried, total));
                }
            }
        };

        launchBtn.setOnClickListener(v -> {
            String service = selectedService[0];
            if (service == null || service.isEmpty()) {
                service = customSvc.getText() == null ? "" : customSvc.getText().toString().trim();
            }
            String host = textOf(hostField);
            String port = textOf(portField);
            String singleL = textOf(singleLogin);
            String singleP = textOf(singlePassword);
            String wlL = textOf(loginWl);
            String wlP = textOf(passwordWl);
            boolean singleMode = modeSingle.isChecked();

            if (service.length() < 2 || host.length() < 3) {
                Toast.makeText(context, R.string.hydra_validation_target, Toast.LENGTH_SHORT).show();
                return;
            }
            if (singleMode) {
                if (singleP.isEmpty()) {
                    Toast.makeText(context, R.string.hydra_validation_creds, Toast.LENGTH_SHORT).show();
                    return;
                }
            } else if (wlP.isEmpty()) {
                Toast.makeText(context, R.string.hydra_validation_wordlist, Toast.LENGTH_SHORT).show();
                return;
            }

            terminalText.setText("");
            terminalContainer.setVisibility(View.VISIBLE);
            expand.setVisibility(View.VISIBLE);
            foundCard.setVisibility(View.GONE);

            HydraEngine.Spec spec = new HydraEngine.Spec(
                    service, host, port,
                    singleMode ? "" : wlL,
                    singleMode ? "" : wlP,
                    singleMode ? singleL : "",
                    singleMode ? singleP : "",
                    (int) threadsSlider.getValue(),
                    verboseChip.isChecked(),
                    firstChip.isChecked()
            );

            if (engineRef[0] == null) {
                engineRef[0] = new HydraEngine(activity, context, core, listener);
            }
            engineRef[0].start(spec);
        });

        stopBtn.setOnClickListener(v -> {
            if (engineRef[0] != null) engineRef[0].kill();
        });

        foundCopy.setOnClickListener(v -> {
            CharSequence text = foundText.getText();
            if (text == null || text.length() == 0) return;
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("hydra", text));
            Toast.makeText(context, R.string.hydra_toast_copied, Toast.LENGTH_SHORT).show();
        });

        expand.setOnClickListener(v -> openFullscreen(context, activity, terminalText));

        closeBtn.setOnClickListener(v -> {
            if (engineRef[0] != null && running[0]) engineRef[0].kill();
            dialog.dismiss();
        });

        shimmer.hideShimmer();
        dialog.show();
    }

    private static void updateLoginVisibility(String service, TextInputLayout loginWlLayout) {
        String single = " redis adam6500 cisco oracle-listener s7-300 snmp vnc rsh rpcap ";
        boolean needLogin = !single.contains(" " + service + " ");
        loginWlLayout.setVisibility(needLogin ? View.VISIBLE : View.GONE);
    }

    private static void openFullscreen(Context context, Activity activity, TextView source) {
        Dialog fs = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fs.setContentView(R.layout.hydra_output_fullscreen);
        Window w = fs.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0F0F0F")));
            w.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        }
        TextView output = fs.findViewById(R.id.hydra_full_output);
        android.widget.ScrollView scroll = fs.findViewById(R.id.hydra_full_scroll);
        TextView metric = fs.findViewById(R.id.hydra_full_metric);
        CharSequence current = source.getText();
        output.setText(current);
        metric.setText(String.valueOf(current == null ? 0 : current.length()));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        android.text.TextWatcher mirror = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                output.setText(s);
                metric.setText(String.valueOf(s == null ? 0 : s.length()));
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        };
        source.addTextChangedListener(mirror);
        fs.setOnDismissListener(d -> source.removeTextChangedListener(mirror));
        MaterialButton close = fs.findViewById(R.id.hydra_full_close);
        close.setOnClickListener(v -> fs.dismiss());
        fs.show();
    }

    private static String textOf(TextView v) {
        CharSequence cs = v.getText();
        return cs == null ? "" : cs.toString().trim();
    }
}
