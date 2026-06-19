package com.zalexdev.stryker.coremanger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Package;
import com.zalexdev.stryker.install.InstallService;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoreManager extends Fragment {

    private Core core;
    private Context context;
    private Activity activity;

    private ArrayList<Package> installedApk = new ArrayList<>();
    private ArrayList<Package> installedPip = new ArrayList<>();
    private ArrayList<Package> visiblePackages = new ArrayList<>();

    private RecyclerView mRecyclerView;
    private CoreAdapter mAdapter;
    private TextInputLayout searchLayout;
    private TextInputEditText search;
    private LinearProgressIndicator progress;
    private Chip apk;
    private Chip pip;
    private Chip filterInstalled;
    private MaterialCardView emptyCard;
    private MaterialCardView listCard;
    private SwipeRefreshLayout refresh;
    private TextView sourceTitle;
    private TextView sourceStatus;
    private TextView countChip;

    private boolean apkMode = true;
    private boolean apkIndexUpdated = false;

    private LinearLayout toolsContainer;
    private final LinkedHashMap<String, ToolRow> toolRows = new LinkedHashMap<>();
    private boolean toolReceiverRegistered = false;

    private static final class ToolDef {
        final String id;
        final int nameRes;
        final int iconRes;
        ToolDef(String id, int nameRes, int iconRes) { this.id = id; this.nameRes = nameRes; this.iconRes = iconRes; }
    }

    private static final class ToolRow {
        final TextView status;
        final ProgressBar spinner;
        final MaterialButton delete;
        ToolRow(TextView status, ProgressBar spinner, MaterialButton delete) {
            this.status = status; this.spinner = spinner; this.delete = delete;
        }
    }

    private final ToolDef[] TOOLS = {
            new ToolDef(InstallService.TOOL_METASPLOIT, R.string.msf_install_card_title, R.drawable.shield),
            new ToolDef(InstallService.TOOL_NUCLEI, R.string.nuclei_status_card_title, R.drawable.webscan),
            new ToolDef(InstallService.TOOL_HYDRA, R.string.hydra_status_card_title, R.drawable.shield),
            new ToolDef("searchsploit", R.string.core_mgr_tool_searchsploit, R.drawable.search),
    };

    private final BroadcastReceiver toolReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getStringExtra(InstallService.EXTRA_LINE) == null) refreshToolRows();
        }
    };

    public CoreManager() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        context = getContext();
        activity = getActivity();
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.coremanager_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        core = new Core(context);

        mRecyclerView = view.findViewById(R.id.package_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        mRecyclerView.setItemViewCacheSize(255);

        searchLayout = view.findViewById(R.id.search_layout);
        search = view.findViewById(R.id.search);
        progress = view.findViewById(R.id.progressbar);
        apk = view.findViewById(R.id.apktoogle);
        pip = view.findViewById(R.id.piptoggle);
        filterInstalled = view.findViewById(R.id.cm_filter_installed);
        emptyCard = view.findViewById(R.id.cm_empty_card);
        listCard = view.findViewById(R.id.cm_list_card);
        refresh = view.findViewById(R.id.cm_refresh);
        sourceTitle = view.findViewById(R.id.cm_source_title);
        sourceStatus = view.findViewById(R.id.cm_source_status);
        countChip = view.findViewById(R.id.cm_count_chip);

        refresh.setOnRefreshListener(this::reloadInstalled);
        apk.setOnClickListener(v -> switchSource(true));
        pip.setOnClickListener(v -> switchSource(false));
        filterInstalled.setOnCheckedChangeListener((b, checked) -> renderList(search.getText() == null ? "" : search.getText().toString()));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderList(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {
                core.putString("cm_search", s == null ? "" : s.toString());
            }
        });

        searchLayout.setEndIconOnClickListener(v -> triggerRemoteSearch());

        toolsContainer = view.findViewById(R.id.cm_tools_container);
        buildToolRows();
        registerToolReceiver();
        refreshToolRows();

        String savedQuery = core.getString("cm_search");
        if (savedQuery != null && !savedQuery.isEmpty()) search.setText(savedQuery);
        switchSource(!"pip".equals(core.getString("cm_source")));
    }

    private void buildToolRows() {
        LayoutInflater inflater = LayoutInflater.from(context);
        toolsContainer.removeAllViews();
        toolRows.clear();
        for (int i = 0; i < TOOLS.length; i++) {
            ToolDef def = TOOLS[i];
            View row = inflater.inflate(R.layout.tool_manage_row, toolsContainer, false);
            ((ImageView) row.findViewById(R.id.tool_icon)).setImageResource(def.iconRes);
            ((TextView) row.findViewById(R.id.tool_name)).setText(def.nameRes);
            TextView status = row.findViewById(R.id.tool_status);
            ProgressBar spinner = row.findViewById(R.id.tool_spinner);
            MaterialButton delete = row.findViewById(R.id.tool_delete);
            if (i == TOOLS.length - 1) row.findViewById(R.id.tool_divider).setVisibility(View.GONE);
            delete.setOnClickListener(v -> confirmDelete(def));
            toolRows.put(def.id, new ToolRow(status, spinner, delete));
            toolsContainer.addView(row);
        }
    }

    private void refreshToolRows() {
        new Thread(() -> {
            Map<String, Boolean> installed = new LinkedHashMap<>();
            Map<String, String> statuses = new LinkedHashMap<>();
            for (ToolDef def : TOOLS) {
                statuses.put(def.id, InstallService.reconcileStatus(context, core, def.id));
                installed.put(def.id, core.isToolInstalled(def.id));
            }
            ui(() -> {
                for (ToolDef def : TOOLS) {
                    ToolRow row = toolRows.get(def.id);
                    if (row == null) continue;
                    boolean isInstalled = Boolean.TRUE.equals(installed.get(def.id));
                    boolean installing = InstallService.STATUS_RUNNING.equals(statuses.get(def.id));
                    applyToolRow(row, isInstalled, installing);
                }
            });
        }, "tools-refresh").start();
    }

    private void applyToolRow(ToolRow row, boolean installed, boolean installing) {
        if (installing) {
            row.status.setText(R.string.tools_state_installing);
            row.spinner.setVisibility(View.VISIBLE);
            row.delete.setVisibility(View.GONE);
        } else if (installed) {
            row.status.setText(R.string.tools_state_installed);
            row.spinner.setVisibility(View.GONE);
            row.delete.setEnabled(true);
            row.delete.setVisibility(View.VISIBLE);
        } else {
            row.status.setText(R.string.tools_state_not_installed);
            row.spinner.setVisibility(View.GONE);
            row.delete.setVisibility(View.GONE);
        }
    }

    private void confirmDelete(ToolDef def) {
        String name = getString(def.nameRes);
        new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.tools_delete_confirm_title, name))
                .setMessage(getString(R.string.tools_delete_confirm_message, name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tools_delete, (d, w) -> performDelete(def))
                .show();
    }

    private void performDelete(ToolDef def) {
        String name = getString(def.nameRes);
        ToolRow row = toolRows.get(def.id);
        if (row != null) {
            row.spinner.setVisibility(View.VISIBLE);
            row.delete.setEnabled(false);
            row.status.setText(getString(R.string.tools_delete_progress, name));
        }
        toaster(getString(R.string.tools_delete_progress, name));
        new Thread(() -> {
            boolean ok = core.uninstallTool(def.id);
            ui(() -> {
                toaster(getString(ok ? R.string.tools_delete_ok : R.string.tools_delete_failed, name));
                refreshToolRows();
            });
        }, "tool-uninstall-" + def.id).start();
    }

    private void registerToolReceiver() {
        IntentFilter filter = new IntentFilter(InstallService.ACTION_UPDATED);
        ContextCompat.registerReceiver(requireContext(), toolReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        toolReceiverRegistered = true;
    }

    private void ui(Runnable r) {
        if (activity != null && isAdded()) activity.runOnUiThread(r);
    }

    @Override
    public void onDestroyView() {
        if (toolReceiverRegistered) {
            try { requireContext().unregisterReceiver(toolReceiver); } catch (IllegalArgumentException ignored) {}
            toolReceiverRegistered = false;
        }
        super.onDestroyView();
    }

    private void switchSource(boolean toApk) {
        apkMode = toApk;
        core.putString("cm_source", toApk ? "apk" : "pip");
        apk.setChecked(toApk);
        pip.setChecked(!toApk);
        sourceTitle.setText(toApk ? R.string.core_mgr_source_apk : R.string.core_mgr_source_pip);
        searchLayout.setHint(toApk ? R.string.core_mgr_search_hint_apk : R.string.core_mgr_search_hint_pip);
        searchLayout.setEndIconDrawable(AppCompatResources.getDrawable(context,
                toApk ? R.drawable.search : R.drawable.download));
        reloadInstalled();
    }

    private void reloadInstalled() {
        freezeUi();
        sourceStatus.setText(R.string.core_mgr_status_loading);
        new Thread(() -> {
            if (apkMode) {
                installedApk = parseApkInstalled(core.customChrootCommand("apk info -v"));
            } else {
                installedPip = parsePipInstalled(core.customChrootCommand("pip list | tail -n +3 | awk '{print $1\"==\"$2}'"));
            }
            ui(() -> {
                renderList(search.getText() == null ? "" : search.getText().toString());
                unfreezeUi();
                if (refresh != null) refresh.setRefreshing(false);
                sourceStatus.setText(R.string.core_mgr_status_ready);
            });
        }).start();
    }

    private void triggerRemoteSearch() {
        String q = search.getText() == null ? "" : search.getText().toString().trim();
        if (q.isEmpty()) return;
        if (!apkMode) {
            installPipDirect(q);
            return;
        }
        freezeUi();
        sourceStatus.setText(R.string.core_mgr_status_updating);
        new Thread(() -> {
            if (!apkIndexUpdated) {
                core.customChrootCommand("apk update");
                apkIndexUpdated = true;
            }
            ArrayList<Package> w = parseApk(core.customChrootCommand("apk search " + q), q);
            ui(() -> {
                visiblePackages = w;
                showResults(visiblePackages);
                sourceStatus.setText(R.string.core_mgr_status_ready);
                unfreezeUi();
            });
        }).start();
    }

    private void installPipDirect(String q) {
        toaster(getString(R.string.core_mgr_installing, q));
        new Thread(() -> {
            boolean ok = Core.contains(core.customChrootCommand("pip3 install --break-system-packages " + q), "Successfully installed");
            ui(() -> {
                toaster(getString(ok ? R.string.core_mgr_install_ok : R.string.core_mgr_install_failed, q));
                if (ok) reloadInstalled();
            });
        }).start();
    }

    private void renderList(String filter) {
        ArrayList<Package> base = apkMode ? installedApk : installedPip;
        ArrayList<Package> out = new ArrayList<>();
        boolean onlyInstalled = filterInstalled.isChecked();
        String f = filter.toLowerCase().trim();
        for (Package p : base) {
            if (onlyInstalled && !p.isInstalled()) continue;
            if (!f.isEmpty() && !p.getName().toLowerCase().contains(f)) continue;
            out.add(p);
        }
        visiblePackages = out;
        showResults(out);
    }

    private void showResults(ArrayList<Package> list) {
        countChip.setText(String.valueOf(list.size()));
        if (list.isEmpty()) {
            listCard.setVisibility(View.GONE);
            emptyCard.setVisibility(View.VISIBLE);
        } else {
            listCard.setVisibility(View.VISIBLE);
            emptyCard.setVisibility(View.GONE);
            mAdapter = new CoreAdapter(context, activity, list, this::reloadInstalled);
            mRecyclerView.setAdapter(mAdapter);
        }
    }

    public void toaster(String msg) {
        if (activity == null) return;
        activity.runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    public void freezeUi() {
        searchLayout.setEnabled(false);
        apk.setEnabled(false);
        pip.setEnabled(false);
        mRecyclerView.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
    }

    public void unfreezeUi() {
        searchLayout.setEnabled(true);
        apk.setEnabled(true);
        pip.setEnabled(true);
        mRecyclerView.setEnabled(true);
        progress.setVisibility(View.INVISIBLE);
    }

    public ArrayList<Package> parseApk(ArrayList<String> out, String q) {
        ArrayList<Package> res = new ArrayList<>();
        for (String pkg : out) {
            Package temp = new Package();
            Matcher r = Pattern.compile("-r[0-9]+").matcher(pkg);
            if (r.find()) pkg = pkg.replace(r.group(), "");
            String[] parts = pkg.split("-");
            if (parts.length < 2) continue;
            String version = parts[parts.length - 1];
            temp.setVersion(version);
            temp.setName(pkg.replace("-" + version, ""));
            for (Package p : installedApk) {
                if (p.getName().equals(temp.getName())) {
                    temp.setInstalled(true);
                    break;
                }
            }
            if (temp.getName().equals(q)) res.add(0, temp);
            else res.add(temp);
            temp.setIsPythonPackage(false);
        }
        return res;
    }

    public ArrayList<Package> parseApkInstalled(ArrayList<String> out) {
        ArrayList<Package> res = new ArrayList<>();
        for (String pkg : out) {
            Package temp = new Package();
            Matcher r = Pattern.compile("-r[0-9]+").matcher(pkg);
            if (r.find()) pkg = pkg.replace(r.group(), "");
            String[] parts = pkg.split("-");
            if (parts.length < 2) continue;
            String version = parts[parts.length - 1];
            temp.setVersion(version);
            temp.setName(pkg.replace("-" + version, ""));
            temp.setInstalled(true);
            temp.setIsPythonPackage(false);
            res.add(temp);
        }
        return res;
    }

    public ArrayList<Package> parsePipInstalled(ArrayList<String> out) {
        ArrayList<Package> res = new ArrayList<>();
        for (String pkg : out) {
            String[] sp = pkg.split("==");
            if (sp.length < 1 || sp[0].trim().isEmpty()) continue;
            Package temp = new Package();
            String version = sp.length > 1 ? sp[1] : "";
            temp.setVersion(version);
            temp.setName(sp[0]);
            temp.setInstalled(true);
            temp.setIsPythonPackage(true);
            res.add(temp);
        }
        return res;
    }
}
