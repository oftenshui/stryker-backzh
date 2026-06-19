package com.zalexdev.stryker.wpair;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Collections;

public class WpairScanner {

    private static final String TAG = "WpairScanner";
    public static final ParcelUuid FAST_PAIR_SERVICE_UUID =
            ParcelUuid.fromString("0000fe2c-0000-1000-8000-00805f9b34fb");

    public interface OnDeviceFound {
        void onFound(FastPairDevice device);
    }

    private final Context appContext;
    private final BluetoothAdapter adapter;
    private final OnDeviceFound callback;

    private boolean scanning = false;
    private boolean scanAll = false;
    private String lastError;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            process(result);
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            for (ScanResult r : results) process(r);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
            WpairLog.info(appContext, null, "scan failed code=" + errorCode);
            scanning = false;
        }
    };

    public WpairScanner(Context ctx, BluetoothAdapter adapter, OnDeviceFound callback) {
        this.appContext = ctx.getApplicationContext();
        this.adapter = adapter;
        this.callback = callback;
    }

    public String lastError() {
        return lastError;
    }

    private BluetoothLeScanner resolveScanner() {
        if (adapter == null) { lastError = "No Bluetooth adapter on this device"; return null; }
        if (!adapter.isEnabled()) { lastError = "Bluetooth is OFF"; return null; }
        BluetoothLeScanner s = adapter.getBluetoothLeScanner();
        if (s == null) { lastError = "BluetoothLeScanner unavailable"; return null; }
        return s;
    }

    @SuppressLint("MissingPermission")
    private void process(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        android.bluetooth.le.ScanRecord record = result.getScanRecord();
        if (record == null) return;
        String addr = result.getDevice().getAddress();
        String name;
        try { name = result.getDevice().getName(); } catch (SecurityException se) { name = null; }
        byte[] serviceData = record.getServiceData(FAST_PAIR_SERVICE_UUID);

        FastPairDevice fpDevice;
        if (serviceData != null) {
            fpDevice = parseFastPair(name, addr, serviceData, result.getRssi(), true);
        } else if (scanAll) {
            fpDevice = new FastPairDevice(name, addr, false, false, null, result.getRssi(), false);
        } else {
            return;
        }
        if (callback != null) callback.onFound(fpDevice);
    }

    private FastPairDevice parseFastPair(String name, String address, byte[] data, int rssi, boolean isFastPair) {
        String modelId = null;
        boolean pairingMode = false;
        boolean hasAccountKeyFilter = false;

        if (data.length > 0) {
            int firstByte = data[0] & 0xFF;

            if (data.length == 3 && (firstByte & 0x80) == 0) {
                StringBuilder sb = new StringBuilder();
                for (byte b : data) sb.append(String.format("%02X", b));
                modelId = sb.toString();
                pairingMode = true;
            }
            else if ((firstByte & 0x60) != 0) {
                hasAccountKeyFilter = true;
            }
            else if (data.length > 3) {
                if ((firstByte & 0x80) == 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 3; i++) sb.append(String.format("%02X", data[i]));
                    modelId = sb.toString();
                }
            }
        }

        return new FastPairDevice(name, address, pairingMode, hasAccountKeyFilter, modelId, rssi, isFastPair);
    }

    public void setScanAll(boolean scanAll) {
        this.scanAll = scanAll;
    }

    @SuppressLint("MissingPermission")
    public boolean start(boolean scanAll) {
        BluetoothLeScanner leScanner = resolveScanner();
        if (leScanner == null) {
            WpairLog.info(appContext, null, "scan abort: " + lastError);
            return false;
        }

        if (scanning) {
            if (this.scanAll == scanAll) return true;
            stop();
        }

        this.scanAll = scanAll;

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();

        try {
            if (scanAll) {
                leScanner.startScan(null, settings, scanCallback);
                WpairLog.info(appContext, null, "scan start (all BLE)");
            } else {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceData(FAST_PAIR_SERVICE_UUID, new byte[0])
                        .build();
                leScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
                WpairLog.info(appContext, null, "scan start (Fast Pair only)");
            }
            scanning = true;
            lastError = null;
            return true;
        } catch (SecurityException se) {
            lastError = "Missing BLUETOOTH_SCAN permission";
            Log.e(TAG, lastError, se);
            WpairLog.info(appContext, null, "scan error: " + lastError);
            return false;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Log.e(TAG, "Failed to start scan", e);
            WpairLog.info(appContext, null, "scan error: " + lastError);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        BluetoothLeScanner leScanner = resolveScanner();
        if (!scanning || leScanner == null) return;
        try {
            leScanner.stopScan(scanCallback);
            scanning = false;
            WpairLog.info(appContext, null, "scan stop");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop scan", e);
        }
    }

    public boolean isScanning() {
        return scanning;
    }
}
