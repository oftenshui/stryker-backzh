package com.zalexdev.stryker.handshakes;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HandshakeStorage extends Fragment {

    private static final String CAPTURE_DIR = "/storage/emulated/0/Stryker/captured";
    private static final Pattern MAC_PATTERN = Pattern.compile("((\\w{2}:){5}\\w{2})");

    private Core core;
    private Context context;
    private Activity activity;
    private RecyclerView recyclerView;
    private MaterialCardView emptyCard;
    private MaterialCardView listCard;
    private TextView statusTitle;
    private TextView metaTotal;
    private TextView metaCracked;
    private TextView metaSize;
    private SwipeRefreshLayout refresh;
    private HandshakesAdapter adapter;

    public HandshakeStorage() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.handshakes_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        core.customCommand("mkdir " + CAPTURE_DIR);

        recyclerView = view.findViewById(R.id.hs_list);
        emptyCard = view.findViewById(R.id.hs_empty_card);
        listCard = view.findViewById(R.id.hs_list_card);
        statusTitle = view.findViewById(R.id.hs_status_title);
        metaTotal = view.findViewById(R.id.hs_meta_total);
        metaCracked = view.findViewById(R.id.hs_meta_cracked);
        metaSize = view.findViewById(R.id.hs_meta_size);
        refresh = view.findViewById(R.id.hs_refresh);
        MaterialButton refreshBtn = view.findViewById(R.id.hs_refresh_btn);

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setItemViewCacheSize(255);

        refresh.setOnRefreshListener(this::reload);
        refreshBtn.setOnClickListener(v -> reload());

        reload();
    }

    private void reload() {
        ArrayList<String> files = core.getListFiles(CAPTURE_DIR);
        if (refresh != null) refresh.setRefreshing(false);

        if (files == null || files.isEmpty()) {
            listCard.setVisibility(View.GONE);
            emptyCard.setVisibility(View.VISIBLE);
            statusTitle.setText(R.string.hs_status_empty);
            metaTotal.setText("0");
            metaCracked.setText("0");
            metaSize.setText("0 KB");
            return;
        }

        listCard.setVisibility(View.VISIBLE);
        emptyCard.setVisibility(View.GONE);

        adapter = new HandshakesAdapter(context, activity, files);
        adapter.setOnChangeListener(this::updateStats);
        recyclerView.setAdapter(adapter);

        updateStats();
    }

    private void updateStats() {
        if (adapter == null) return;
        int total = adapter.hslist.size();
        int cracked = 0;
        long bytes = 0;
        for (String path : adapter.hslist) {
            String mac = path;
            Matcher m = MAC_PATTERN.matcher(path);
            if (m.find()) mac = m.group(0);
            String stored = core.getString(mac);
            if (stored != null && !stored.isEmpty()) cracked++;
            File f = path.startsWith("/") ? new File(path) : new File(CAPTURE_DIR, path);
            if (f.exists()) bytes += f.length();
        }
        statusTitle.setText(getString(R.string.hs_status_count, total));
        metaTotal.setText(String.valueOf(total));
        metaCracked.setText(String.valueOf(cracked));
        metaSize.setText(humanSize(bytes));
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
