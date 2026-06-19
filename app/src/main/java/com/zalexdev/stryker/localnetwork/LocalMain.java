package com.zalexdev.stryker.localnetwork;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.localnetwork.utils.AdvancedLocalScanner;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalMain extends Fragment {

    public Core core;
    public Context context;
    public Activity activity;
    public MainActivity mainActivity;

    public SwipeRefreshLayout refresh;
    public LinearProgressIndicator progress;
    public WebView titleloader;

    public MaterialCardView firewall_card;
    public MaterialCardView emptyCard;
    public MaterialCardView listCard;

    public LottieAnimationView img;
    public MaterialTextView text;
    public MaterialTextView textSub;
    public MaterialTextView subtitle;
    public MaterialTextView networkSsid;
    public MaterialTextView networkStatus;
    public MaterialTextView networkGateway;
    public MaterialTextView networkSubnet;
    public MaterialTextView networkCount;
    public MaterialButton rescanBtn;

    private RecyclerView mRecyclerView;
    private LocalAdapter mAdapter;
    public ArrayList<Device> devicesmain = new ArrayList<>();

    private boolean scanning = false;

    private static final String KEY_SCANNING = "lan_last_scanning";

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private AdvancedLocalScanner scanner;

    public LocalMain() {
    }

    private boolean uiSafe() {
        return alive.get() && activity != null && isAdded();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.local_fragment, container, false);
        context = getContext();
        activity = getActivity();
        mainActivity = (MainActivity) activity;
        alive.set(true);
        core = new Core(context);
        reloadDevicesFromPersistentSource();

        bindViews(view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setItemViewCacheSize(64);
        mAdapter = new LocalAdapter(context, activity, devicesmain);
        mRecyclerView.setAdapter(mAdapter);

        refresh.setColorSchemeColors(
                getResources().getColor(R.color.stryker_accent),
                0xFFAB47BC);
        refresh.setOnRefreshListener(this::triggerScan);
        rescanBtn.setOnClickListener(v -> triggerScan());

        View statusRow = view.findViewById(R.id.local_status_row);
        if (statusRow != null) {
            statusRow.setOnClickListener(v -> pickLocalInterface());
        }

        View gatewayCell = view.findViewById(R.id.network_gateway_cell);
        View subnetCell = view.findViewById(R.id.network_subnet_cell);
        if (gatewayCell != null) gatewayCell.setOnClickListener(v -> openScanTargetEditor());
        if (subnetCell != null) subnetCell.setOnClickListener(v -> openScanTargetEditor());

        updateNetworkInfo();
        renderListState();

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        reloadDevicesFromPersistentSource();
        if (mAdapter != null) {
            mAdapter.devices = devicesmain;
            mAdapter.notifyDataSetChanged();
        }
        updateNetworkInfo();
        renderListState();

        boolean wasScanning = core != null && core.getBoolean(KEY_SCANNING);

        if (devicesmain.isEmpty()) {
            if (isWifiConnected()) {
                startScan();
            } else {
                showNoWifi();
            }
        } else if (wasScanning && isWifiConnected()) {
            triggerScan();
        } else {
            scanning = false;
            if (core != null) core.putBoolean(KEY_SCANNING, false);
            updateScanningUi();
        }
    }

    private void reloadDevicesFromPersistentSource() {
        ArrayList<Device> restored = null;
        if (mainActivity != null && mainActivity.getDevices() != null
                && !mainActivity.getDevices().isEmpty()) {
            restored = mainActivity.getDevices();
        } else if (core != null) {
            ArrayList<Device> last = core.getLastNetworkScan();
            if (last != null && !last.isEmpty()) {
                restored = last;
                if (mainActivity != null) {
                    mainActivity.setDevices(restored);
                }
            }
        }
        if (restored != null) {
            devicesmain = restored;
        }
    }

    private void bindViews(View view) {
        mRecyclerView = view.findViewById(R.id.local_list);
        refresh = view.findViewById(R.id.local_refresh);
        img = view.findViewById(R.id.local_img);
        text = view.findViewById(R.id.local_text);
        textSub = view.findViewById(R.id.local_text_sub);
        subtitle = view.findViewById(R.id.local_subtitle);
        progress = view.findViewById(R.id.nmap_progressbar);
        titleloader = view.findViewById(R.id.title_loader);
        firewall_card = view.findViewById(R.id.firewall_warn);
        emptyCard = view.findViewById(R.id.local_empty_card);
        listCard = view.findViewById(R.id.local_list_card);

        networkSsid = view.findViewById(R.id.network_ssid);
        networkStatus = view.findViewById(R.id.network_status);
        networkGateway = view.findViewById(R.id.network_gateway);
        networkSubnet = view.findViewById(R.id.network_subnet);
        networkCount = view.findViewById(R.id.network_count);
        rescanBtn = view.findViewById(R.id.network_rescan);
    }

    private void triggerScan() {
        if (scanning) {
            refresh.setRefreshing(false);
            return;
        }
        if (!isWifiConnected()) {
            refresh.setRefreshing(false);
            showNoWifi();
            return;
        }
        devicesmain.clear();
        mAdapter.notifyDataSetChanged();
        if (mainActivity != null) {
            mainActivity.setDevices(devicesmain);
        }
        startScan();
    }

    public void startScan() {
        scanning = true;
        core.putBoolean(KEY_SCANNING, true);
        updateScanningUi();
        progress.setIndeterminate(false);
        progress.setMax(110);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);

        scanner = new AdvancedLocalScanner(activity, context, core.getString("wlan_scan")) {
            @Override
            public void onProgressUpdate(int prog) {
                if (!uiSafe()) return;
                if (prog <= 100) {
                    progress.setProgress(prog, true);
                } else {
                    progress.setIndeterminate(true);
                }
                if (prog > 109) {
                    scanning = false;
                    core.putBoolean(KEY_SCANNING, false);
                    progress.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    updateScanningUi();
                    updateNetworkInfo();
                    renderListState();
                }
            }

            @Override
            public void onDeviceAdded(Device device) {
                if (!uiSafe()) return;
                devicesmain.add(device);
                mAdapter.notifyItemInserted(devicesmain.size() - 1);
                renderListState();
                updateNetworkInfo();
            }

            @Override
            public void onDeviceChanged(Device device, int pos) {
                if (!uiSafe()) return;
                try {
                    if (pos >= 0 && pos < devicesmain.size()) {
                        devicesmain.set(pos, device);
                        mAdapter.notifyItemChanged(pos);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onStarted() {
            }

            @Override
            public void onFinishedScan() {
                scanning = false;
                if (core != null) core.putBoolean(KEY_SCANNING, false);
                if (mainActivity != null) {
                    mainActivity.setDevices(devicesmain);
                }
                if (!uiSafe()) return;
                updateScanningUi();
                renderListState();
            }
        };
    }

    private void renderListState() {
        boolean hasDevices = !devicesmain.isEmpty();
        listCard.setVisibility(hasDevices ? View.VISIBLE : View.GONE);
        emptyCard.setVisibility(hasDevices ? View.GONE : View.VISIBLE);
        networkCount.setText(String.valueOf(devicesmain.size()));
    }

    private void updateScanningUi() {
        if (scanning) {
            subtitle.setText(R.string.local_subtitle_scanning);
            networkStatus.setText(R.string.local_status_scanning);
            if (devicesmain.isEmpty()) {
                text.setText(R.string.local_scan);
                textSub.setText(R.string.local_empty_body);
            }
        } else if (isWifiConnected()) {
            subtitle.setText(R.string.local_subtitle_idle);
            networkStatus.setText(R.string.local_status_done);
        } else {
            subtitle.setText(R.string.local_subtitle_no_wifi);
            networkStatus.setText(R.string.local_status_offline);
        }
    }

    private void showNoWifi() {
        scanning = false;
        if (core != null) core.putBoolean(KEY_SCANNING, false);
        progress.setVisibility(View.GONE);
        refresh.setRefreshing(false);
        emptyCard.setVisibility(View.VISIBLE);
        listCard.setVisibility(View.GONE);
        text.setText(R.string.local_no_wifi_title);
        textSub.setText(R.string.local_no_wifi_body);
        img.setAnimation(R.raw.error);
        img.playAnimation();
        networkSsid.setText(R.string.local_status_offline);
        networkStatus.setText(R.string.local_status_offline);
        networkGateway.setText("—");
        networkSubnet.setText("—");
        networkCount.setText("0");
        subtitle.setText(R.string.local_subtitle_no_wifi);
    }

    private void updateNetworkInfo() {
        if (context == null) return;
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return;
        if (!isWifiConnected()) {
            networkSsid.setText(R.string.local_status_offline);
            return;
        }

        WifiInfo info = wifiManager.getConnectionInfo();
        if (info != null) {
            String ssid = info.getSSID();
            if (ssid != null) {
                ssid = ssid.replace("\"", "");
                if (ssid.equals("<unknown ssid>") || ssid.isEmpty()) {
                    ssid = getString(R.string.local_status_connected);
                }
                networkSsid.setText(ssid);
            }
        }

        String override = core.getString("local_scan_target");
        int accent = 0xFFAB47BC;
        int defaultText = getResources().getColor(android.R.color.primary_text_light);
        if (override != null && !override.isEmpty() && override.contains("/")) {
            String[] parts = override.split("/");
            networkGateway.setText(parts[0]);
            networkSubnet.setText("/" + (parts.length > 1 ? parts[1] : ""));
            networkGateway.setTextColor(accent);
            networkSubnet.setTextColor(accent);
            return;
        }

        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        if (dhcp != null) {
            networkGateway.setText(intToIP(dhcp.gateway));
            networkSubnet.setText(intToIP(dhcp.netmask));
            networkGateway.setTextColor(defaultText);
            networkSubnet.setTextColor(defaultText);
        }
    }

    private void pickLocalInterface() {
        if (context == null) return;
        new Thread(() -> {
            ArrayList<String> ifaces = core.getInterfacesList();
            if (!uiSafe()) return;
            activity.runOnUiThread(() -> {
                if (!uiSafe()) return;
                showLocalInterfacePicker(ifaces);
            });
        }).start();
    }

    private void showLocalInterfacePicker(ArrayList<String> interfaces) {
        if (context == null || activity == null) return;
        String[] items = new String[interfaces.size() + 1];
        for (int i = 0; i < interfaces.size(); i++) {
            items[i] = interfaces.get(i);
        }
        items[items.length - 1] = context.getString(R.string.customvalue);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pick)
                .setItems(items, (di, i) -> {
                    if (i == items.length - 1) {
                        promptCustomLocalInterface();
                    } else {
                        applyLocalInterface(items[i]);
                    }
                })
                .show();
    }

    private void promptCustomLocalInterface() {
        final Dialog valueDialog = new Dialog(context);
        valueDialog.setContentView(R.layout.input_dialog);
        Window vw = valueDialog.getWindow();
        if (vw != null) {
            vw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            vw.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        TextView title = valueDialog.findViewById(R.id.title);
        TextInputEditText value = valueDialog.findViewById(R.id.value);
        MaterialButton ok = valueDialog.findViewById(R.id.ok);
        MaterialButton cancel = valueDialog.findViewById(R.id.cancel);
        title.setText(R.string.customvalue);
        cancel.setOnClickListener(v -> valueDialog.dismiss());
        ok.setOnClickListener(v -> {
            String entered = value.getText() == null ? "" : value.getText().toString().trim();
            if (entered.isEmpty()) {
                value.setError(context.getString(R.string.customvalue));
                return;
            }
            valueDialog.dismiss();
            applyLocalInterface(entered);
        });
        valueDialog.show();
    }

    private void applyLocalInterface(String iface) {
        core.putString("wlan_scan", iface);
        networkSsid.setText(iface);
        triggerScan();
    }

    private void openScanTargetEditor() {
        if (context == null) return;
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.local_scan_target_dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        com.google.android.material.textfield.TextInputLayout gwLayout = dialog.findViewById(R.id.scan_target_gateway_layout);
        com.google.android.material.textfield.TextInputLayout cidrLayout = dialog.findViewById(R.id.scan_target_cidr_layout);
        TextInputEditText gwInput = dialog.findViewById(R.id.scan_target_gateway);
        TextInputEditText cidrInput = dialog.findViewById(R.id.scan_target_cidr);
        MaterialButton apply = dialog.findViewById(R.id.scan_target_apply);
        MaterialButton auto = dialog.findViewById(R.id.scan_target_auto);
        MaterialButton cancel = dialog.findViewById(R.id.scan_target_cancel);

        String existing = core.getString("local_scan_target");
        if (existing != null && existing.contains("/")) {
            String[] parts = existing.split("/");
            gwInput.setText(parts[0]);
            cidrInput.setText(parts.length > 1 ? parts[1] : "24");
        } else {
            String currentGw = networkGateway.getText() == null ? "" : networkGateway.getText().toString();
            if (!"—".equals(currentGw)) gwInput.setText(currentGw);
            cidrInput.setText("24");
        }

        cancel.setOnClickListener(v -> dialog.dismiss());
        auto.setOnClickListener(v -> {
            core.putString("local_scan_target", "");
            dialog.dismiss();
            triggerScan();
        });
        apply.setOnClickListener(v -> {
            String gw = gwInput.getText() == null ? "" : gwInput.getText().toString().trim();
            String cidrStr = cidrInput.getText() == null ? "" : cidrInput.getText().toString().trim();
            if (!isValidIp(gw)) {
                gwLayout.setError(getString(R.string.local_scan_target_invalid_ip));
                return;
            }
            int cidr;
            try {
                cidr = Integer.parseInt(cidrStr);
            } catch (NumberFormatException e) {
                cidrLayout.setError(getString(R.string.local_scan_target_invalid_cidr));
                return;
            }
            if (cidr < 1 || cidr > 32) {
                cidrLayout.setError(getString(R.string.local_scan_target_invalid_cidr));
                return;
            }
            String net = networkAddress(gw, cidr) + "/" + cidr;
            core.putString("local_scan_target", net);
            dialog.dismiss();
            triggerScan();
        });

        dialog.show();
    }

    private boolean isValidIp(String s) {
        if (s == null) return false;
        String[] parts = s.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private String networkAddress(String ip, int cidr) {
        String[] parts = ip.split("\\.");
        long addr = 0;
        for (String p : parts) {
            addr = (addr << 8) | Integer.parseInt(p);
        }
        long mask = cidr == 0 ? 0L : (0xFFFFFFFFL << (32 - cidr)) & 0xFFFFFFFFL;
        long net = addr & mask;
        return ((net >> 24) & 0xff) + "." + ((net >> 16) & 0xff) + "." + ((net >> 8) & 0xff) + "." + (net & 0xff);
    }

    public boolean isWifiConnected() {
        if (context == null) return false;
        ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager == null) return false;
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi != null && mWifi.isConnected();
    }

    private String intToIP(int ipAddress) {
        return String.format(Locale.ENGLISH, "%d.%d.%d.%d",
                ipAddress & 0xff,
                (ipAddress >> 8) & 0xff,
                (ipAddress >> 16) & 0xff,
                (ipAddress >> 24) & 0xff);
    }

    public String getGateway() {
        if (context == null) return "";
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return "";
        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        if (dhcp == null) return "";
        return intToIP(dhcp.gateway);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        alive.set(false);
        if (scanner != null) {
            try {
                if (AdvancedLocalScanner.process != null) {
                    AdvancedLocalScanner.process.destroy();
                }
            } catch (Exception ignored) {
            }
            try {
                if (scanner.mainThread != null) {
                    scanner.mainThread.interrupt();
                }
            } catch (Exception ignored) {
            }
            scanner = null;
        }
        if (titleloader != null) {
            try {
                titleloader.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}
