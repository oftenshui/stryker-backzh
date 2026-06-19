package com.zalexdev.stryker.macchanger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.AdvancedProcess;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.SimpleProcess;
import com.zalexdev.stryker.utils.Utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class MACChangerFragment extends Fragment {

    private static final Pattern MAC_FORMAT = Pattern.compile("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}");

    private static final String[] OUI_APPLE   = {"3C:5A:B4", "F0:18:98", "A8:5C:2C", "B8:E8:56"};
    private static final String[] OUI_INTEL   = {"3C:97:0E", "DC:A6:32", "00:1B:21", "94:65:9C"};
    private static final String[] OUI_SAMSUNG = {"E8:50:8B", "2C:8A:72", "B0:72:BF", "D0:17:6A"};
    private static final String[] OUI_HUAWEI  = {"00:9A:CD", "5C:7D:5E", "F4:DB:E6", "B8:C1:A2"};

    private static final Map<String, String> OUI_LOOKUP = buildOuiLookup();

    private static Map<String, String> buildOuiLookup() {
        Map<String, String> m = new HashMap<>();
        for (String oui : OUI_APPLE)   m.put(stripColons(oui), "Apple");
        for (String oui : OUI_INTEL)   m.put(stripColons(oui), "Intel");
        for (String oui : OUI_SAMSUNG) m.put(stripColons(oui), "Samsung");
        for (String oui : OUI_HUAWEI)  m.put(stripColons(oui), "Huawei");
        m.put("001A11", "Google");
        m.put("FCFBFB", "Cisco");
        m.put("0050BA", "D-Link");
        m.put("F4F26D", "TP-Link");
        m.put("E840F2", "Realtek");
        m.put("A402B9", "Xiaomi");
        m.put("4CFCAA", "ASUSTek");
        m.put("00154D", "Nokia");
        m.put("00904C", "Espressif");
        m.put("2C81E1", "Espressif");
        return m;
    }

    private static String stripColons(String mac) {
        return mac.replace(":", "").toUpperCase();
    }

    private Activity activity;
    private Context context;
    private Core core;

    private MaterialTextView ifaceName, ifaceSub, statusPill;
    private MaterialTextView currentMac, currentVendor, permanentMac;
    private View permanentRow;
    private MaterialButton currentCopy, permanentCopy;

    private TextInputLayout ifaceLayout, macLayout;
    private AutoCompleteTextView ifaceInput;
    private TextInputEditText macInput;
    private MaterialButton changeMac, resetMac;
    private MaterialCardView android12Card;

    private String permanentMacValue = "";
    private String pendingChangedMac = "";

    private static final String KEY_MAC_INPUT = "macchanger_mac_input";
    private static final String KEY_PENDING   = "macchanger_pending";
    private static final String KEY_BUSY      = "macchanger_busy";

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private boolean uiReady() {
        return alive.get() && activity != null && isAdded();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        context = getContext();
        core = new Core(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        return inflater.inflate(R.layout.fragment_macchanger, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle saved) {
        alive.set(true);
        bindViews(view);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android12Card.setVisibility(View.VISIBLE);
        }

        ifaceInput.setOnItemClickListener((parent, v, pos, id) -> {
            String pick = ifaceInput.getText().toString();
            core.putString("macchanger_wlan", pick);
            ifaceName.setText(pick);
            refreshAddresses();
        });

        ifaceLayout.setStartIconOnClickListener(v -> loadInterfaces());

        macLayout.setEndIconOnClickListener(v -> setMacInput(randomMac()));

        currentCopy.setOnClickListener(v -> copyToClipboard(currentMac.getText().toString()));
        permanentCopy.setOnClickListener(v -> copyToClipboard(permanentMacValue));

        bindPresetChip(view.findViewById(R.id.chip_random),  null);
        bindPresetChip(view.findViewById(R.id.chip_apple),   OUI_APPLE);
        bindPresetChip(view.findViewById(R.id.chip_intel),   OUI_INTEL);
        bindPresetChip(view.findViewById(R.id.chip_samsung), OUI_SAMSUNG);
        bindPresetChip(view.findViewById(R.id.chip_huawei),  OUI_HUAWEI);

        macInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String value = s.toString();
                core.putString(KEY_MAC_INPUT, value);
                if (value.isEmpty()) {
                    macLayout.setError(null);
                    return;
                }
                macLayout.setError(MAC_FORMAT.matcher(value).matches()
                        ? null : getString(R.string.mac_invalid_format));
            }
        });

        changeMac.setOnClickListener(v -> applyMac(ifaceInput.getText().toString(),
                macInput.getText() == null ? "" : macInput.getText().toString()));
        resetMac.setOnClickListener(v -> applyMac(ifaceInput.getText().toString(), permanentMacValue));

        restoreState();

        loadInterfaces();
        core.threadChrootCommand("apk add macchanger");
    }

    @Override
    public void onDestroyView() {
        alive.set(false);
        super.onDestroyView();
    }

    private void bindViews(View view) {
        ifaceName     = view.findViewById(R.id.mac_iface_name);
        ifaceSub      = view.findViewById(R.id.mac_iface_sub);
        statusPill    = view.findViewById(R.id.mac_status_pill);
        currentMac    = view.findViewById(R.id.mac_current_value);
        currentVendor = view.findViewById(R.id.mac_current_vendor);
        permanentMac  = view.findViewById(R.id.mac_permanent_value);
        permanentRow  = view.findViewById(R.id.mac_permanent_row);
        currentCopy   = view.findViewById(R.id.mac_current_copy);
        permanentCopy = view.findViewById(R.id.mac_permanent_copy);
        ifaceLayout   = view.findViewById(R.id.iface_layout);
        macLayout     = view.findViewById(R.id.macaddress_layout);
        ifaceInput    = view.findViewById(R.id.iface);
        macInput      = view.findViewById(R.id.macaddress);
        changeMac     = view.findViewById(R.id.change_mac);
        resetMac      = view.findViewById(R.id.permanent_mac);
        android12Card = view.findViewById(R.id.mac_android12_card);
    }

    private void bindPresetChip(Chip chip, String[] ouiPool) {
        if (chip == null) return;
        chip.setOnClickListener(v -> setMacInput(ouiPool == null ? randomMac() : randomMacWithOui(ouiPool)));
    }

    private void setMacInput(String mac) {
        macInput.setText(mac);
        if (mac != null) macInput.setSelection(mac.length());
    }

    private void restoreState() {
        String savedMac = core.getString(KEY_MAC_INPUT);
        if (savedMac != null && !savedMac.isEmpty()) {
            macInput.setText(savedMac);
            macInput.setSelection(savedMac.length());
        }
        pendingChangedMac = core.getString(KEY_PENDING);
        if (pendingChangedMac == null) pendingChangedMac = "";
        if (core.getBoolean(KEY_BUSY)) {
            core.putBoolean(KEY_BUSY, false);
        }
    }

    private void copyToClipboard(String value) {
        if (value == null || value.isEmpty() || "—".equals(value)) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("mac", value));
        core.toaster(activity, getString(R.string.mac_copied_toast));
    }

    private void loadInterfaces() {
        new Thread(() -> {
            ArrayList<String> interfaces = core.getInterfacesList();
            if (interfaces.isEmpty()) return;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item, interfaces);
            String prev = core.getString("macchanger_wlan");
            String pick = prev.isEmpty() || !interfaces.contains(prev)
                    ? interfaces.get(interfaces.size() - 1) : prev;
            if (!uiReady()) return;
            activity.runOnUiThread(() -> {
                if (!uiReady()) return;
                ifaceInput.setAdapter(adapter);
                ifaceInput.setText(pick, false);
                ifaceName.setText(pick);
                refreshAddresses();
            });
        }).start();
    }

    private void refreshAddresses() {
        String iface = ifaceInput.getText().toString();
        if (iface.isEmpty()) return;
        new Thread(() -> {
            String currentValue = readInterfaceMac(iface);
            String permanentValue = readPermanentMac(iface);
            if (!uiReady()) return;
            activity.runOnUiThread(() -> {
                if (!uiReady()) return;
                if (currentValue != null) {
                    currentMac.setText(currentValue);
                    showVendor(currentValue);
                }
                permanentMacValue = permanentValue == null ? "" : permanentValue;
                if (!permanentMacValue.isEmpty()
                        && MAC_FORMAT.matcher(permanentMacValue).matches()) {
                    permanentRow.setVisibility(View.VISIBLE);
                    permanentMac.setText(permanentMacValue);
                    resetMac.setEnabled(!permanentMacValue.equalsIgnoreCase(currentValue));
                } else {
                    permanentRow.setVisibility(View.GONE);
                    resetMac.setEnabled(false);
                }
            });
        }).start();
    }

    private String readInterfaceMac(String iface) {
        ArrayList<String> out = core.customCommand(
                "ip addr show " + iface
                + " | sed -n \"s/.*link\\/ether \\(\\([0-9A-f]\\{2\\}:\\)\\{5\\}[0-9A-f]\\{2\\}\\).*/\\1/p\"");
        return out.isEmpty() ? null : out.get(0);
    }

    private String readPermanentMac(String iface) {
        ArrayList<String> out = core.customChrootCommand("macchanger -s " + iface);
        for (String line : out) {
            String match = Utils.matchString("Permanent MAC: (([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2})", line, 1);
            if (match != null && !match.isEmpty()) return match;
        }
        return null;
    }

    private void showVendor(String mac) {
        if (mac == null || mac.length() < 8) {
            currentVendor.setVisibility(View.GONE);
            return;
        }
        String oui = mac.substring(0, 8).toUpperCase().replace(":", "");
        String vendor = OUI_LOOKUP.get(oui);
        if (vendor == null && core.vendorDB != null) vendor = core.vendorDB.get(oui);
        if (vendor == null && (mac.charAt(1) == '2' || mac.charAt(1) == '6'
                || mac.charAt(1) == 'a' || mac.charAt(1) == 'e'
                || mac.charAt(1) == 'A' || mac.charAt(1) == 'E')) {
            vendor = "Locally-administered (spoofed)";
        }
        if (vendor == null || vendor.isEmpty()) {
            currentVendor.setVisibility(View.GONE);
        } else {
            currentVendor.setText(vendor);
            currentVendor.setVisibility(View.VISIBLE);
        }
    }

    private static String randomMac() {
        SecureRandom rnd = new SecureRandom();
        byte[] b = new byte[6];
        rnd.nextBytes(b);
        b[0] = (byte) ((b[0] & 0xFC) | 0x02);
        return formatMac(b);
    }

    private static String randomMacWithOui(String[] pool) {
        SecureRandom rnd = new SecureRandom();
        String oui = pool[rnd.nextInt(pool.length)];
        byte[] tail = new byte[3];
        rnd.nextBytes(tail);
        return oui + String.format(":%02x:%02x:%02x", tail[0], tail[1], tail[2]).toLowerCase();
    }

    private static String formatMac(byte[] b) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @SuppressLint("SdCardPath")
    private void applyMac(String iface, String mac) {
        if (mac == null || !MAC_FORMAT.matcher(mac).matches()) {
            macLayout.setError(getString(R.string.mac_invalid_format));
            return;
        }
        pendingChangedMac = mac;
        core.putString(KEY_PENDING, mac);
        core.putBoolean(KEY_BUSY, true);
        setBusy(true, R.string.mac_pill_changing);

        boolean inlineSpoofPath = iface.matches("(s|)wlan0")
                && Build.VERSION.SDK_INT != Build.VERSION_CODES.R;
        if (inlineSpoofPath) {
            runChangemacScript(iface, mac);
        } else {
            runMacchangerBinary(iface, mac);
        }
    }

    private void runChangemacScript(String iface, String mac) {
        new AdvancedProcess(activity, context,
                "/data/data/com.zalexdev.stryker/files/changemac " + iface + " " + mac, false) {
            @Override
            public void onFinished(ArrayList<String> outputList) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    waitForReassociation(iface);
                } else {
                    boolean ok = Core.contains(outputList, "successfully changed");
                    if (!uiReady()) return;
                    activity.runOnUiThread(() -> { if (uiReady()) finishApply(ok); });
                }
            }

            @Override
            public void onNewLine(String line) {
                if (line.contains("MAC address format error") || line.contains("Failed to change")
                        || line.contains("successfully changed") || line.contains("address changed")) {
                    process.destroy();
                }
            }

            @Override
            public void onEvent(String line) {}
        };
    }

    private void runMacchangerBinary(String iface, String mac) {
        new Thread(() -> {
            core.customCommand("ip link set " + iface + " down", true);
            new SimpleProcess(activity, "macchanger " + iface + " -m " + mac, true) {
                @Override
                public void onFinished(ArrayList<String> outputList) {
                    boolean ok = Core.contains(outputList, "New MAC:");
                    if (uiReady()) activity.runOnUiThread(() -> { if (uiReady()) finishApply(ok); });
                    new Thread(() -> core.customCommand("ip link set " + iface + " up")).start();
                }
            };
        }).start();
    }

    private void waitForReassociation(String iface) {
        if (uiReady()) activity.runOnUiThread(() -> {
            if (!uiReady()) return;
            statusPill.setText(R.string.mac_pill_waiting_net);
            statusPill.setTextColor(0xFFEF6C00);
            ifaceSub.setText(R.string.mac_waiting_net);
        });
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + 10_000L;
            while (alive.get() && System.currentTimeMillis() < deadline) {
                if (isWifiNetworkUp()) {
                    String reread = readInterfaceMac(iface);
                    boolean ok = reread != null && reread.equalsIgnoreCase(pendingChangedMac);
                    if (!uiReady()) return;
                    activity.runOnUiThread(() -> {
                        if (!uiReady()) return;
                        if (ok) {
                            finishApply(true);
                        } else {
                            showAndroid12FailureDialog();
                            finishApply(false);
                        }
                    });
                    return;
                }
                try { Thread.sleep(250); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!uiReady()) return;
            activity.runOnUiThread(() -> {
                if (!uiReady()) return;
                core.toaster(activity, getString(R.string.mac_waiting_timeout));
                finishApply(false);
            });
        }).start();
    }

    private boolean isWifiNetworkUp() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network nw = cm.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void finishApply(boolean ok) {
        core.putBoolean(KEY_BUSY, false);
        setBusy(false, ok ? R.string.mac_pill_changed : R.string.mac_pill_error);
        statusPill.setTextColor(ok ? 0xFF2E7D32 : 0xFFC62828);
        core.toaster(activity, getString(ok ? R.string.mac_success : R.string.mac_failed));
        refreshAddresses();
    }

    private void setBusy(boolean busy, int pillRes) {
        statusPill.setText(pillRes);
        statusPill.setTextColor(busy ? 0xFFEF6C00 : 0xFF757575);
        ifaceLayout.setEnabled(!busy);
        macLayout.setEnabled(!busy);
        changeMac.setEnabled(!busy);
        resetMac.setEnabled(!busy && !permanentMacValue.isEmpty());
    }

    private void showAndroid12FailureDialog() {
        View view = getLayoutInflater().inflate(R.layout.fragment_macchanger_a12_dialog, null);
        TextView message = view.findViewById(R.id.message);
        message.setText(Html.fromHtml(
                "Android 12+ rebinds MAC on associate. To make the spoofed MAC stick, "
                + "install <a href=\"https://github.com/LSPosed/LSPosed\">LSPosed</a> + the "
                + "<a href=\"https://github.com/DavidBerdik/MACsposed\">MACsposed</a> module.",
                Html.FROM_HTML_MODE_LEGACY));
        message.setMovementMethod(new LinkMovementMethod());
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.mac_failed)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (di, i) -> {})
                .setCancelable(true)
                .show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
