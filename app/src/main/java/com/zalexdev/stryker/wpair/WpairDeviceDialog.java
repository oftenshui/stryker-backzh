package com.zalexdev.stryker.wpair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayDeque;
import java.util.Iterator;

public class WpairDeviceDialog extends BottomSheetDialogFragment {

    private static final String ARG_ADDRESS = "address";
    private static final String ARG_NAME = "name";
    private static final String ARG_STATUS = "status";

    public interface Listener {
        void onTest();
        void onMagic();
        void onWriteAccountKey();
        void onFloodKeys();
        void onConnectHfp();
        void onStartLive();
        void onStopLive();
        void onStartRecord();
        void onStopRecord();
        void onOpenTerminal();
    }

    private Listener listener;
    private TextView progressView;
    private String targetAddress;
    private boolean live;
    private boolean recording;

    private final BroadcastReceiver onLogUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            refreshProgress();
        }
    };

    public static WpairDeviceDialog create(FastPairDevice device, Listener listener) {
        WpairDeviceDialog d = new WpairDeviceDialog();
        Bundle b = new Bundle();
        b.putString(ARG_ADDRESS, device.getAddress());
        b.putString(ARG_NAME, device.displayName());
        b.putString(ARG_STATUS, device.getStatus().name());
        d.setArguments(b);
        d.listener = listener;
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_wpair_device_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        targetAddress = args == null ? null : args.getString(ARG_ADDRESS);
        String name = args == null ? "Device" : args.getString(ARG_NAME);
        String status = args == null ? "UNKNOWN" : args.getString(ARG_STATUS, "UNKNOWN");

        view.<TextView>findViewById(R.id.wpair_dlg_name).setText(name);
        view.<TextView>findViewById(R.id.wpair_dlg_subtitle).setText(targetAddress);
        view.<TextView>findViewById(R.id.wpair_dlg_status).setText(status);
        progressView = view.findViewById(R.id.wpair_dlg_progress);

        MaterialButton liveBtn = view.findViewById(R.id.wpair_dlg_live);
        MaterialButton recordBtn = view.findViewById(R.id.wpair_dlg_record);

        view.<MaterialButton>findViewById(R.id.wpair_dlg_test).setOnClickListener(v -> {
            if (listener != null) listener.onTest();
        });
        view.<MaterialButton>findViewById(R.id.wpair_dlg_magic).setOnClickListener(v -> {
            if (listener != null) listener.onMagic();
        });
        view.<MaterialButton>findViewById(R.id.wpair_dlg_write_key).setOnClickListener(v -> {
            if (listener != null) listener.onWriteAccountKey();
        });
        view.<MaterialButton>findViewById(R.id.wpair_dlg_flood).setOnClickListener(v -> {
            if (listener != null) listener.onFloodKeys();
        });
        view.<MaterialButton>findViewById(R.id.wpair_dlg_hfp).setOnClickListener(v -> {
            if (listener != null) listener.onConnectHfp();
        });

        liveBtn.setOnClickListener(v -> {
            if (listener == null) return;
            live = !live;
            if (live) {
                listener.onStartLive();
                liveBtn.setText(R.string.wpair_btn_stop);
            } else {
                listener.onStopLive();
                liveBtn.setText(R.string.wpair_btn_live);
            }
        });
        recordBtn.setOnClickListener(v -> {
            if (listener == null) return;
            recording = !recording;
            if (recording) {
                listener.onStartRecord();
                recordBtn.setText(R.string.wpair_btn_stop);
            } else {
                listener.onStopRecord();
                recordBtn.setText(R.string.wpair_btn_record);
            }
        });

        view.<MaterialButton>findViewById(R.id.wpair_dlg_terminal).setOnClickListener(v -> {
            if (listener != null) listener.onOpenTerminal();
            dismissAllowingStateLoss();
        });
        view.<MaterialButton>findViewById(R.id.wpair_dlg_close).setOnClickListener(v -> dismiss());

        refreshProgress();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(WpairLog.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(onLogUpdate, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(onLogUpdate, f);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(onLogUpdate);
        } catch (IllegalArgumentException ignored) {}
    }

    private void refreshProgress() {
        if (progressView == null || targetAddress == null) return;
        File f = WpairLog.logFile(requireContext());
        if (!f.exists()) return;
        ArrayDeque<String> tail = new ArrayDeque<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("[" + targetAddress + "]")) {
                    if (tail.size() >= 6) tail.removeFirst();
                    tail.add(line);
                }
            }
        } catch (Exception ignored) {}

        if (tail.isEmpty()) {
            progressView.setText(R.string.wpair_dlg_idle);
            return;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = tail.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) sb.append('\n');
        }
        progressView.setText(sb.toString());
    }
}
