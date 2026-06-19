package com.zalexdev.stryker.wpair;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WpairRecordingsFragment extends Fragment {

    private static final String PREF_SCROLL_POS = "wpair_recordings_scroll_pos";

    private File outputDir;
    private RecordingsAdapter adapter;
    private RecyclerView recycler;
    private View emptyView;
    private MediaPlayer currentPlayer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wpair_recordings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        outputDir = WpairFragment.recordingsDir(requireContext());

        recycler = view.findViewById(R.id.wpair_recordings_list);
        emptyView = view.findViewById(R.id.wpair_recordings_empty);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RecordingsAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    int pos = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                    if (pos != RecyclerView.NO_POSITION && getContext() != null) {
                        new Core(getContext()).putInt(PREF_SCROLL_POS, pos);
                    }
                }
            }
        });

        view.<MaterialButton>findViewById(R.id.wpair_rec_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
        view.<MaterialButton>findViewById(R.id.wpair_rec_refresh).setOnClickListener(v -> reload());

        reload();
        restoreScrollPosition();
    }

    private void restoreScrollPosition() {
        if (getContext() == null) {
            return;
        }
        final int pos = new Core(getContext()).getInt(PREF_SCROLL_POS, 0);
        if (pos <= 0 || recycler == null) {
            return;
        }
        recycler.post(() -> {
            if (adapter != null && pos < adapter.getItemCount()) {
                recycler.scrollToPosition(pos);
            }
        });
    }

    private void reload() {
        File[] files = outputDir.listFiles((dir, name) ->
                name.endsWith(".m4a") || name.endsWith(".pcm"));
        List<File> list;
        if (files == null) {
            list = Collections.emptyList();
        } else {
            list = new ArrayList<>(Arrays.asList(files));
            list.sort(new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
        }
        adapter.set(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void play(File file) {
        stopPlayback();
        try {
            currentPlayer = new MediaPlayer();
            currentPlayer.setDataSource(file.getAbsolutePath());
            currentPlayer.prepare();
            currentPlayer.setOnCompletionListener(mp -> stopPlayback());
            currentPlayer.start();
            Toast.makeText(getContext(), "Playing " + file.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Play failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private void stopPlayback() {
        if (currentPlayer != null) {
            try { currentPlayer.stop(); } catch (Exception ignored) {}
            try { currentPlayer.release(); } catch (Exception ignored) {}
            currentPlayer = null;
        }
    }

    private void share(File file) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", file);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(file.getName().endsWith(".pcm") ? "application/octet-stream" : "audio/mp4");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share recording"));
    }

    private void confirmDelete(File file) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete recording?")
                .setMessage(file.getName())
                .setPositiveButton("Delete", (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(getContext(), "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPlayback();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("0.0").format(kb) + " KB";
        return new DecimalFormat("0.00").format(kb / 1024.0) + " MB";
    }

    private final class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.VH> {
        private final ArrayList<File> files = new ArrayList<>();

        void set(List<File> newFiles) {
            files.clear();
            files.addAll(newFiles);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wpair_recording, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File f = files.get(position);
            h.name.setText(f.getName());
            h.meta.setText(formatSize(f.length()));
            h.play.setOnClickListener(v -> play(f));
            h.share.setOnClickListener(v -> share(f));
            h.delete.setOnClickListener(v -> confirmDelete(f));
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView name, meta;
            final MaterialButton play, share, delete;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.wpair_rec_name);
                meta = v.findViewById(R.id.wpair_rec_meta);
                play = v.findViewById(R.id.wpair_rec_play);
                share = v.findViewById(R.id.wpair_rec_share);
                delete = v.findViewById(R.id.wpair_rec_delete);
            }
        }
    }
}
