package com.zalexdev.stryker.arsenal;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Sploit;
import com.zalexdev.stryker.searchsploit.ExploitWebview;
import com.zalexdev.stryker.utils.Core;

public class SploitDetailSheet extends BottomSheetDialogFragment {

    interface Listener {
        void onAddToHub(Sploit sploit);
    }

    private static final String ARG_TITLE = "title";
    private static final String ARG_ID = "id";
    private static final String ARG_PLATFORM = "platform";
    private static final String ARG_TYPE = "type";
    private static final String ARG_AUTHOR = "author";
    private static final String ARG_DATE = "date";
    private static final String ARG_PATH = "path";

    private Listener listener;
    private Core core;
    private Sploit sploit;

    public static SploitDetailSheet newInstance(Sploit sploit, @Nullable Listener listener) {
        SploitDetailSheet s = new SploitDetailSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, sploit.getTitle());
        args.putString(ARG_ID, sploit.getId());
        args.putString(ARG_PLATFORM, sploit.getPlatform());
        args.putString(ARG_TYPE, sploit.getType());
        args.putString(ARG_AUTHOR, sploit.getAuthor());
        args.putString(ARG_DATE, sploit.getDate());
        args.putString(ARG_PATH, sploit.getPath());
        s.setArguments(args);
        s.listener = listener;
        return s;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bs = (BottomSheetDialog) d;
            View sheetView = bs.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheetView != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheetView);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_arsenal_sploit_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context context = getContext();
        core = new Core(context);

        Bundle args = getArguments();
        if (args == null) {
            dismiss();
            return;
        }
        sploit = new Sploit();
        sploit.setTitle(args.getString(ARG_TITLE));
        sploit.setId(args.getString(ARG_ID));
        sploit.setPlatform(args.getString(ARG_PLATFORM));
        sploit.setType(args.getString(ARG_TYPE));
        sploit.setAuthor(args.getString(ARG_AUTHOR));
        sploit.setDate(args.getString(ARG_DATE));
        sploit.setPath(args.getString(ARG_PATH));

        ((MaterialTextView) view.findViewById(R.id.sd_title)).setText(orDash(sploit.getTitle()));
        ((MaterialTextView) view.findViewById(R.id.sd_id)).setText("EDB-ID: " + orDash(sploit.getId()));
        ((MaterialTextView) view.findViewById(R.id.sd_platform)).setText(orDash(sploit.getPlatform()));
        ((MaterialTextView) view.findViewById(R.id.sd_type)).setText(orDash(sploit.getType()));
        ((MaterialTextView) view.findViewById(R.id.sd_author)).setText(orDash(sploit.getAuthor()));
        ((MaterialTextView) view.findViewById(R.id.sd_date)).setText(orDash(sploit.getDate()));
        ((MaterialTextView) view.findViewById(R.id.sd_path)).setText(orDash(sploit.getPath()));

        ImageView icon = view.findViewById(R.id.sd_icon);
        String platform = sploit.getPlatform() == null ? "" : sploit.getPlatform().toLowerCase();
        icon.setImageResource(iconFor(platform, sploit.getType() == null ? "" : sploit.getType().toLowerCase()));
        icon.setColorFilter(ContextCompat.getColor(context, R.color.night_contrast));

        MaterialButton viewBtn = view.findViewById(R.id.sd_view);
        MaterialButton saveBtn = view.findViewById(R.id.sd_save);
        MaterialButton hubBtn = view.findViewById(R.id.sd_add_hub);

        viewBtn.setOnClickListener(v -> {
            boolean hasId = sploit.getId() != null && !sploit.getId().isEmpty();
            boolean hasPath = sploit.getPath() != null && !sploit.getPath().isEmpty();
            if (!hasId && !hasPath) {
                core.toaster(getString(R.string.arsenal_db_missing_id));
                return;
            }
            Intent i = new Intent(context, ExploitWebview.class);
            i.putExtra("exploit", sploit.getId());
            i.putExtra("path", sploit.getPath());
            startActivity(i);
        });

        saveBtn.setOnClickListener(v -> {
            if (sploit.getPath() == null || sploit.getPath().isEmpty()) {
                core.toaster(getString(R.string.arsenal_db_missing_path));
                return;
            }
            new Thread(() -> {
                String src = sploit.getPath().startsWith("/") ? sploit.getPath() : "/" + sploit.getPath();
                core.copyFile("/data/local/stryker/release/exploitdb" + src,
                        core.getStorage() + "Stryker/exploits/");
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            core.toaster(getString(R.string.arsenal_db_saved_file)));
                }
            }).start();
        });

        hubBtn.setOnClickListener(v -> {
            if (listener != null) listener.onAddToHub(sploit);
            dismiss();
        });
    }

    private int iconFor(String platform, String type) {
        if (type.contains("webapps")) return R.drawable.web;
        if (platform.contains("windows")) return R.drawable.windows_icon;
        if (platform.contains("linux") || platform.contains("unix")) return R.drawable.linux;
        if (platform.contains("android")) return R.drawable.iphone;
        if (platform.contains("ios") || platform.contains("macos")) return R.drawable.apple;
        if (platform.contains("hardware")) return R.drawable.board;
        if (platform.contains("multiple")) return R.drawable.devices;
        if (platform.contains("freebsd")) return R.drawable.freebsd;
        if (platform.contains("solaris")) return R.drawable.solaris;
        return R.drawable.question;
    }

    private static String orDash(@Nullable String s) {
        if (s == null) return "—";
        String trimmed = s.trim();
        return trimmed.isEmpty() ? "—" : trimmed;
    }
}
