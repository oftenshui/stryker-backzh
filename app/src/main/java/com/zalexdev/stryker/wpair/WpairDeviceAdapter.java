package com.zalexdev.stryker.wpair;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WpairDeviceAdapter extends RecyclerView.Adapter<WpairDeviceAdapter.VH> {

    public enum Filter { FAST_PAIR, ALL, VULNERABLE, PAIRED }

    public interface Listener {
        void onTest(FastPairDevice device);
        void onMagic(FastPairDevice device);
        void onOpenActions(FastPairDevice device);
    }

    private static final long REBUILD_DEBOUNCE_MS = 250L;
    private static final Object PAYLOAD_RSSI = new Object();

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ArrayList<FastPairDevice> all = new ArrayList<>();
    private final ArrayList<FastPairDevice> visible = new ArrayList<>();
    private final HashMap<String, String> progressByAddress = new HashMap<>();

    private Filter activeFilter = Filter.FAST_PAIR;
    private final Set<String> pairedAddresses = new HashSet<>();

    private boolean rebuildScheduled;
    private final Runnable rebuildRunnable = this::rebuildVisible;

    public WpairDeviceAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setPairedAddresses(Set<String> paired) {
        pairedAddresses.clear();
        pairedAddresses.addAll(paired);
    }

    public void markPaired(String address) {
        if (address == null) return;
        pairedAddresses.add(address);
        scheduleRebuild();
    }

    public void upsert(FastPairDevice device) {
        if (device == null || device.getAddress() == null) return;
        int idx = indexInAll(device.getAddress());
        if (idx >= 0) {
            FastPairDevice existing = all.get(idx);
            device.setStatus(existing.getStatus());
            all.set(idx, device);
            int vIdx = indexInVisible(device.getAddress());
            if (vIdx >= 0) {
                visible.set(vIdx, device);
                notifyItemChanged(vIdx, PAYLOAD_RSSI);
            }
            return;
        }
        all.add(device);
        scheduleRebuild();
    }

    public void setProgress(String address, String message) {
        if (address == null) return;
        if (message == null) progressByAddress.remove(address);
        else progressByAddress.put(address, message);
        int idx = indexInVisible(address);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_RSSI);
    }

    public void updateStatus(String address, FastPairDevice.Status status) {
        int idx = indexInAll(address);
        if (idx < 0) return;
        all.get(idx).setStatus(status);
        int vIdx = indexInVisible(address);
        if (vIdx >= 0) notifyItemChanged(vIdx);
    }

    public void clearAll() {
        int n = visible.size();
        all.clear();
        visible.clear();
        progressByAddress.clear();
        if (n > 0) notifyItemRangeRemoved(0, n);
    }

    public void setFilter(Filter filter) {
        if (filter == this.activeFilter) return;
        this.activeFilter = filter;
        rebuildVisible();
    }

    public int totalCount() { return all.size(); }

    public int vulnerableCount() {
        int c = 0;
        for (FastPairDevice d : all) if (d.getStatus() == FastPairDevice.Status.VULNERABLE) c++;
        return c;
    }

    public int pairedCount() {
        int c = 0;
        for (FastPairDevice d : all) if (pairedAddresses.contains(d.getAddress())) c++;
        return c;
    }

    private int indexInAll(String address) {
        for (int i = 0; i < all.size(); i++) {
            if (address.equals(all.get(i).getAddress())) return i;
        }
        return -1;
    }

    private int indexInVisible(String address) {
        for (int i = 0; i < visible.size(); i++) {
            if (address.equals(visible.get(i).getAddress())) return i;
        }
        return -1;
    }

    private void scheduleRebuild() {
        if (rebuildScheduled) return;
        rebuildScheduled = true;
        handler.postDelayed(rebuildRunnable, REBUILD_DEBOUNCE_MS);
    }

    private void rebuildVisible() {
        rebuildScheduled = false;
        visible.clear();
        for (FastPairDevice d : all) {
            switch (activeFilter) {
                case FAST_PAIR:
                    if (d.isFastPair()) visible.add(d);
                    break;
                case ALL:
                    visible.add(d);
                    break;
                case VULNERABLE:
                    if (d.getStatus() == FastPairDevice.Status.VULNERABLE) visible.add(d);
                    break;
                case PAIRED:
                    if (pairedAddresses.contains(d.getAddress())) visible.add(d);
                    break;
            }
        }
        visible.sort(new Comparator<FastPairDevice>() {
            @Override
            public int compare(FastPairDevice a, FastPairDevice b) {
                return Integer.compare(b.getRssi(), a.getRssi());
            }
        });
        notifyDataSetChanged();
    }

    public List<FastPairDevice> currentDevices() {
        return new ArrayList<>(all);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wpair_device, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_RSSI)) {
            FastPairDevice d = visible.get(position);
            h.rssi.setText(d.getRssi() + " dBm");
            bindProgress(h, d);
            return;
        }
        super.onBindViewHolder(h, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FastPairDevice d = visible.get(position);
        h.name.setText(d.displayName());
        h.address.setText(d.getAddress());
        h.rssi.setText(d.getRssi() + " dBm");

        String modelInfo;
        String vendor = d.manufacturer();
        if (d.getModelId() != null) {
            modelInfo = (vendor == null ? "" : vendor + " · ") + "ID " + d.getModelId();
        } else if (vendor != null) {
            modelInfo = vendor;
        } else {
            modelInfo = d.isFastPair() ? "Fast Pair" : "BLE";
        }
        h.model.setText(modelInfo);

        h.pairingBadge.setVisibility(d.isPairingMode() ? View.VISIBLE : View.GONE);
        bindStatus(h, d);
        bindProgress(h, d);
        bindButtons(h, d);
    }

    private void bindProgress(VH h, FastPairDevice d) {
        String progress = progressByAddress.get(d.getAddress());
        if (progress != null && !progress.isEmpty()) {
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setText(progress);
        } else {
            h.progress.setVisibility(View.GONE);
        }
    }

    private void bindButtons(VH h, FastPairDevice d) {
        boolean isFp = d.isFastPair();
        boolean inFlight = d.getStatus() == FastPairDevice.Status.TESTING;
        boolean canTest = isFp && !inFlight;
        boolean canMagic = isFp && !inFlight && d.getStatus() != FastPairDevice.Status.PATCHED;

        h.test.setEnabled(canTest);
        h.test.setAlpha(canTest ? 1f : 0.45f);
        h.magic.setEnabled(canMagic);
        h.magic.setAlpha(canMagic ? 1f : 0.45f);
        h.test.setOnClickListener(v -> listener.onTest(d));
        h.magic.setOnClickListener(v -> listener.onMagic(d));
        h.more.setOnClickListener(v -> listener.onOpenActions(d));
        h.itemView.setOnClickListener(v -> listener.onOpenActions(d));
    }

    private void bindStatus(VH h, FastPairDevice d) {
        int color;
        String label;
        switch (d.getStatus()) {
            case VULNERABLE:
                color = 0xFFC62828;
                label = context.getString(R.string.wpair_status_vulnerable);
                break;
            case PATCHED:
                color = 0xFF2E7D32;
                label = context.getString(R.string.wpair_status_patched);
                break;
            case TESTING:
                color = 0xFFF9A825;
                label = context.getString(R.string.wpair_status_testing);
                break;
            case ERROR:
                color = 0xFFEF6C00;
                label = context.getString(R.string.wpair_status_error);
                break;
            case NOT_TESTED:
            default:
                if (pairedAddresses.contains(d.getAddress())) {
                    color = 0xFF2E7D32;
                    label = context.getString(R.string.wpair_status_paired);
                } else {
                    color = 0xFF757575;
                    label = context.getString(R.string.wpair_status_not_tested);
                }
        }
        h.status.setText(label);
        h.status.setTextColor(color);
    }

    @Override
    public int getItemCount() { return visible.size(); }

    @Override
    public long getItemId(int position) {
        FastPairDevice d = visible.get(position);
        return d.getAddress() == null ? RecyclerView.NO_ID : d.getAddress().hashCode();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name, address, rssi, model, status, progress;
        final TextView pairingBadge;
        final MaterialButton test, magic, more;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.wpair_dev_name);
            address = v.findViewById(R.id.wpair_dev_address);
            rssi = v.findViewById(R.id.wpair_dev_rssi);
            model = v.findViewById(R.id.wpair_dev_model);
            status = v.findViewById(R.id.wpair_dev_status);
            progress = v.findViewById(R.id.wpair_dev_progress);
            pairingBadge = v.findViewById(R.id.wpair_dev_badge_pairing);
            test = v.findViewById(R.id.wpair_btn_test);
            magic = v.findViewById(R.id.wpair_btn_magic);
            more = v.findViewById(R.id.wpair_btn_more);
        }
    }
}
