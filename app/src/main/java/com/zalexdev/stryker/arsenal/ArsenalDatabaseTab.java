package com.zalexdev.stryker.arsenal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.install.LogAdapter;
import com.zalexdev.stryker.appintro.install.LogLine;
import com.zalexdev.stryker.custom.Sploit;
import com.zalexdev.stryker.utils.Core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArsenalDatabaseTab extends Fragment {

    private static final String SEARCHSPLOIT_PATH =
            "/data/local/stryker/release/exploitdb/searchsploit";

    private static final String PREF_LAST_QUERY = "arsenal_db_last_query";

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Activity activity;
    private Context context;
    private Core core;

    private View installRoot;
    private View searchRoot;

    private TextInputLayout queryLayout;
    private TextInputEditText queryInput;
    private LinearProgressIndicator queryProgress;
    private RecyclerView resultsList;
    private View hint;
    private ChipGroup suggestions;
    private MaterialTextView statusSubtitle;
    private MaterialTextView statusCountChip;
    private MaterialCardView emptyCard;
    private MaterialTextView emptyText;

    private static final String[] SUGGESTED_QUERIES = {
            "eternalblue",
            "log4j",
            "printnightmare",
            "apache",
            "wordpress",
            "sql injection",
            "kernel",
            "sudo",
            "openssh"
    };

    private final List<Sploit> results = new ArrayList<>();
    private SploitAdapter adapter;

    private MaterialTextView installTitle;
    private MaterialTextView installSubtitle;
    private ImageView installIcon;
    private ProgressBar installSpinner;
    private LinearProgressIndicator installProgress;
    private MaterialButton installButton;
    private View stagesHeader;
    private LinearLayout stagesContainer;
    private View logHeader;
    private RecyclerView logRecycler;
    private LogAdapter logAdapter;

    private enum Stage { CHECKING, GIT, CLONE, VERIFY, DONE }

    private static final String[] STAGE_TITLES = {
            "Checking dependencies",
            "Installing git",
            "Cloning exploit-db (~200 MB)",
            "Verifying installation",
            "Ready"
    };

    private enum RowState { PENDING, ACTIVE, DONE, FAILED }

    private static final class StageRow {
        final TextView title;
        final ImageView icon;
        final ProgressBar spinner;
        final FrameLayout indicator;

        StageRow(TextView title, ImageView icon, ProgressBar spinner, FrameLayout indicator) {
            this.title = title;
            this.icon = icon;
            this.spinner = spinner;
            this.indicator = indicator;
        }
    }

    private final EnumMap<Stage, StageRow> stageRows = new EnumMap<>(Stage.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_arsenal_database, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);

        installRoot = view.findViewById(R.id.db_install_root);
        searchRoot = view.findViewById(R.id.db_search_root);

        bindSearch(view);
        bindInstall(view);

        evaluateState();
        ExploitDbInstaller.get().attach(installListener);

        restoreLastQuery();
    }

    private void restoreLastQuery() {
        if (queryInput == null) return;
        String last = core.getString(PREF_LAST_QUERY);
        if (last == null) last = "";
        last = last.trim();
        if (last.isEmpty()) return;
        queryInput.setText(last);
        queryInput.setSelection(last.length());
        if (core.checkFile(SEARCHSPLOIT_PATH)) {
            runQuery();
        }
    }

    private void bindSearch(View view) {
        queryLayout = view.findViewById(R.id.db_query_layout);
        queryInput = view.findViewById(R.id.db_query);
        queryProgress = view.findViewById(R.id.db_progress);
        resultsList = view.findViewById(R.id.db_list);
        hint = view.findViewById(R.id.db_hint);
        suggestions = view.findViewById(R.id.db_suggestions);
        statusSubtitle = view.findViewById(R.id.db_status_subtitle);
        statusCountChip = view.findViewById(R.id.db_status_count_chip);
        emptyCard = view.findViewById(R.id.db_empty_card);
        emptyText = view.findViewById(R.id.db_empty_text);

        resultsList.setLayoutManager(new LinearLayoutManager(context));
        resultsList.setItemViewCacheSize(64);
        adapter = new SploitAdapter(context, results, new SploitAdapter.Listener() {
            @Override
            public void onOpen(Sploit sploit) {
                openDetail(sploit);
            }

            @Override
            public void onQuickSave(Sploit sploit) {
                addToHub(sploit);
            }
        });
        resultsList.setAdapter(adapter);

        populateSuggestions();
        refreshStatus();

        queryLayout.setEndIconOnClickListener(v -> runQuery());
        queryInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runQuery();
                return true;
            }
            return false;
        });
    }

    private void populateSuggestions() {
        if (suggestions == null) return;
        suggestions.removeAllViews();
        for (String query : SUGGESTED_QUERIES) {
            Chip chip = new Chip(context);
            chip.setText(query);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(R.color.light_lite_contrast);
            chip.setChipStrokeWidth(0f);
            chip.setOnClickListener(v -> {
                queryInput.setText(query);
                queryInput.setSelection(query.length());
                runQuery();
            });
            suggestions.addView(chip);
        }
    }

    private void refreshStatus() {
        if (statusCountChip == null) return;
        new Thread(() -> {
            ArrayList<String> raw = core.customChrootCommand(
                    "wc -l /exploitdb/files_exploits.csv 2>/dev/null | awk '{print $1}'", true);
            if (cancelled.get()) return;
            String count = null;
            for (String line : raw) {
                String t = line == null ? "" : line.trim();
                if (!t.isEmpty() && t.matches("\\d+")) {
                    try {
                        long n = Long.parseLong(t);
                        if (n > 1) count = formatThousands(n - 1);
                    } catch (NumberFormatException ignored) { }
                    break;
                }
            }
            final String resolved = count;
            runOnUi(() -> {
                if (resolved == null) {
                    statusCountChip.setText(R.string.arsenal_db_status_ready);
                } else {
                    statusCountChip.setText(getString(R.string.arsenal_db_status_count, resolved));
                }
            });
        }).start();
    }

    private static String formatThousands(long n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    private void bindInstall(View view) {
        installTitle = view.findViewById(R.id.db_install_title);
        installSubtitle = view.findViewById(R.id.db_install_subtitle);
        installIcon = view.findViewById(R.id.db_install_icon);
        installSpinner = view.findViewById(R.id.db_install_spinner);
        installProgress = view.findViewById(R.id.db_install_progress);
        installButton = view.findViewById(R.id.db_install_button);

        stagesHeader = view.findViewById(R.id.db_install_stages_header);
        stagesContainer = view.findViewById(R.id.db_install_stages);
        logHeader = view.findViewById(R.id.db_install_log_header);
        logRecycler = view.findViewById(R.id.db_install_log);

        logRecycler.setLayoutManager(new LinearLayoutManager(context));
        logAdapter = new LogAdapter(context);
        logRecycler.setAdapter(logAdapter);

        buildStageRows();

        installButton.setOnClickListener(v -> startInstall());
    }

    private void evaluateState() {
        new Thread(() -> {
            boolean installed = core.checkFile(SEARCHSPLOIT_PATH);
            if (cancelled.get()) return;
            runOnUi(() -> {
                ExploitDbInstaller inst = ExploitDbInstaller.get();
                if (installed && !inst.isRunning()) {
                    installRoot.setVisibility(View.GONE);
                    searchRoot.setVisibility(View.VISIBLE);
                } else {
                    installRoot.setVisibility(View.VISIBLE);
                    searchRoot.setVisibility(View.GONE);
                    if (inst.isRunning()) {
                        enterInstallingUi();
                    } else {
                        showIdleInstallUi();
                    }
                }
            });
        }).start();
    }

    private void runQuery() {
        String q = queryInput.getText() == null ? "" : queryInput.getText().toString().trim();
        if (q.isEmpty()) {
            queryLayout.setError(getString(R.string.arsenal_db_search_required));
            return;
        }
        queryLayout.setError(null);
        core.putString(PREF_LAST_QUERY, q);
        hideKeyboard();
        queryProgress.setVisibility(View.VISIBLE);
        hint.setVisibility(View.GONE);
        emptyCard.setVisibility(View.GONE);
        results.clear();
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            ArrayList<String> out = core.customChrootCommand(
                    "/exploitdb/searchsploit " + escape(q) + "  --json");
            if (cancelled.get()) return;
            List<Sploit> parsed = parseAll(out);
            runOnUi(() -> {
                results.addAll(parsed);
                adapter.notifyDataSetChanged();
                queryProgress.setVisibility(View.GONE);
                if (parsed.isEmpty()) {
                    hint.setVisibility(View.VISIBLE);
                    emptyCard.setVisibility(View.VISIBLE);
                    emptyText.setText(getString(R.string.arsenal_db_no_results_body, q));
                }
            });
        }).start();
    }

    private void hideKeyboard() {
        if (activity == null || queryInput == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(queryInput.getWindowToken(), 0);
    }

    private static String escape(String q) {
        return q.replace("'", "").replace("`", "").replace("$", "");
    }

    private static List<Sploit> parseAll(List<String> lines) {
        List<Sploit> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String j = raw.trim();
            if (j.endsWith(",")) j = j.substring(0, j.length() - 1);
            if (!j.startsWith("{") || !j.endsWith("}")) continue;
            try {
                JSONObject obj = new JSONObject(j);
                Sploit s = new Sploit();
                s.setTitle(obj.optString("Title"));
                s.setDate(obj.optString("Date_Published"));
                s.setAuthor(obj.optString("Author"));
                s.setType(obj.optString("Type"));
                s.setPlatform(obj.optString("Platform"));
                s.setPath(obj.optString("Path"));
                s.setId(obj.optString("EDB-ID"));
                out.add(s);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void openDetail(Sploit sploit) {
        SploitDetailSheet sheet = SploitDetailSheet.newInstance(sploit, new SploitDetailSheet.Listener() {
            @Override
            public void onAddToHub(Sploit src) {
                addToHub(src);
            }
        });
        sheet.show(getChildFragmentManager(), "arsenal_sploit_detail");
    }

    private void addToHub(Sploit sploit) {
        String chrootPath = sploit.getPath();
        if (chrootPath == null || chrootPath.isEmpty()) {
            core.toaster(getString(R.string.arsenal_db_missing_path));
            return;
        }
        if (!chrootPath.startsWith("/")) chrootPath = "/" + chrootPath;
        String fileName = chrootPath.substring(chrootPath.lastIndexOf('/') + 1);
        final String chrootPathFinal = chrootPath;

        com.zalexdev.stryker.custom.Exploit prefill = new com.zalexdev.stryker.custom.Exploit();
        prefill.setPath(fileName);
        if (sploit.getTitle() != null) prefill.setTitle(sploit.getTitle());
        AddExploitSheet sheet = AddExploitSheet.newInstance(prefill, () -> {
            core.toaster(getString(R.string.arsenal_db_copying));
            new Thread(() -> {
                core.copyFile("/data/local/stryker/release/exploitdb" + chrootPathFinal,
                        core.getStorage() + "Stryker/exploits/");
                if (cancelled.get()) return;
                runOnUi(() -> {
                    Fragment parent = getParentFragment();
                    if (parent instanceof ArsenalFragment) {
                        ((ArsenalFragment) parent).refreshSubtitle();
                    }
                    core.toaster(getString(R.string.arsenal_db_added_to_hub));
                });
            }).start();
        });
        sheet.show(getChildFragmentManager(), "arsenal_add_after_db");
    }

    private final ExploitDbInstaller.Listener installListener = new ExploitDbInstaller.Listener() {
        @Override
        public void onLog(LogLine line) {
            if (logAdapter == null) return;
            logAdapter.append(line);
            if (logAdapter.size() > 0) logRecycler.scrollToPosition(logAdapter.size() - 1);
        }

        @Override
        public void onStage(int stage, int rowState) {
            StageRow row = stageRows.get(Stage.values()[stage]);
            if (row != null) applyRowState(row, RowState.values()[rowState]);
        }

        @Override
        public void onProgress(boolean indeterminate, int percent) {
            if (installProgress == null) return;
            installProgress.setVisibility(View.VISIBLE);
            installProgress.setIndeterminate(indeterminate);
            if (!indeterminate) installProgress.setProgress(percent, true);
        }

        @Override
        public void onSubtitle(CharSequence text) {
            if (installSubtitle != null && text != null && text.length() > 0) {
                installSubtitle.setText(text);
            }
        }

        @Override
        public void onFinished(boolean success) {
            onInstallFinished(success);
        }
    };

    private void startInstall() {
        ExploitDbInstaller inst = ExploitDbInstaller.get();
        if (inst.isRunning()) return;
        logAdapter.clear();
        resetStages();
        enterInstallingUi();
        inst.start(activity, context);
    }

    private void showIdleInstallUi() {
        installButton.setEnabled(true);
        installButton.setVisibility(View.VISIBLE);
        installSpinner.setVisibility(View.GONE);
        installIcon.setVisibility(View.VISIBLE);
        installIcon.setImageResource(R.drawable.database);
        installIcon.clearColorFilter();
        installProgress.setVisibility(View.GONE);
        stagesHeader.setVisibility(View.GONE);
        stagesContainer.setVisibility(View.GONE);
        logHeader.setVisibility(View.GONE);
        logRecycler.setVisibility(View.GONE);
    }

    private void enterInstallingUi() {
        installButton.setEnabled(false);
        installButton.setVisibility(View.GONE);
        installSpinner.setVisibility(View.VISIBLE);
        installIcon.setVisibility(View.GONE);
        installProgress.setVisibility(View.VISIBLE);
        stagesHeader.setVisibility(View.VISIBLE);
        stagesContainer.setVisibility(View.VISIBLE);
        logHeader.setVisibility(View.VISIBLE);
        logRecycler.setVisibility(View.VISIBLE);
    }

    private void onInstallFinished(boolean ok) {
        if (!isAdded() || getView() == null) return;
        if (ok) {
            installSpinner.setVisibility(View.GONE);
            installIcon.setVisibility(View.VISIBLE);
            installIcon.setImageResource(R.drawable.done);
            installIcon.setColorFilter(ContextCompat.getColor(context, R.color.green),
                    PorterDuff.Mode.SRC_IN);
            installSubtitle.setText(R.string.arsenal_db_install_done);
            installProgress.setIndeterminate(false);
            installProgress.setProgress(100, true);

            Fragment parent = getParentFragment();
            if (parent instanceof ArsenalFragment) {
                ((ArsenalFragment) parent).refreshSubtitle();
            }
            refreshStatus();
            installRoot.postDelayed(() -> {
                if (!isAdded() || getView() == null) return;
                installRoot.setVisibility(View.GONE);
                searchRoot.setVisibility(View.VISIBLE);
            }, 750);
        } else {
            installSpinner.setVisibility(View.GONE);
            installIcon.setVisibility(View.VISIBLE);
            installIcon.setImageResource(R.drawable.error);
            installIcon.setColorFilter(ContextCompat.getColor(context, R.color.red),
                    PorterDuff.Mode.SRC_IN);
            installSubtitle.setText(R.string.arsenal_db_install_failed);
            installProgress.setVisibility(View.GONE);
            installButton.setEnabled(true);
            installButton.setVisibility(View.VISIBLE);
            installButton.setText(R.string.try_again);
        }
    }

    @Override
    public void onDestroyView() {
        cancelled.set(true);
        ExploitDbInstaller.get().detach(installListener);
        super.onDestroyView();
    }

    private void buildStageRows() {
        stagesContainer.removeAllViews();
        stageRows.clear();
        LayoutInflater inflater = LayoutInflater.from(context);
        int idx = 0;
        for (Stage stage : Stage.values()) {
            View row = inflater.inflate(R.layout.install_stage_row, stagesContainer, false);
            TextView title = row.findViewById(R.id.stage_title);
            ImageView icon = row.findViewById(R.id.stage_icon);
            ProgressBar spinner = row.findViewById(R.id.stage_spinner);
            FrameLayout indicator = row.findViewById(R.id.stage_indicator);
            title.setText(STAGE_TITLES[idx++]);
            StageRow handles = new StageRow(title, icon, spinner, indicator);
            applyRowState(handles, RowState.PENDING);
            stageRows.put(stage, handles);
            stagesContainer.addView(row);
        }
    }

    private void resetStages() {
        runOnUi(() -> {
            for (StageRow row : stageRows.values()) applyRowState(row, RowState.PENDING);
        });
    }

    private void applyRowState(StageRow row, RowState state) {
        int color;
        switch (state) {
            case ACTIVE:
                color = ContextCompat.getColor(context, R.color.stryker_accent);
                row.spinner.setVisibility(View.VISIBLE);
                row.icon.setVisibility(View.GONE);
                row.title.setTypeface(null, Typeface.BOLD);
                break;
            case DONE:
                color = ContextCompat.getColor(context, R.color.green);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.VISIBLE);
                row.icon.setImageResource(R.drawable.done);
                row.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                row.title.setTypeface(null, Typeface.NORMAL);
                break;
            case FAILED:
                color = ContextCompat.getColor(context, R.color.red);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.VISIBLE);
                row.icon.setImageResource(R.drawable.error);
                row.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                row.title.setTypeface(null, Typeface.BOLD);
                break;
            case PENDING:
            default:
                color = ContextCompat.getColor(context, R.color.grey);
                row.spinner.setVisibility(View.GONE);
                row.icon.setVisibility(View.GONE);
                row.title.setTypeface(null, Typeface.NORMAL);
                break;
        }
        row.title.setTextColor(color);
        if (row.indicator.getBackground() != null) {
            row.indicator.getBackground().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            row.indicator.getBackground().setAlpha(60);
        }
    }

    private void runOnUi(Runnable r) {
        if (activity == null || !isAdded()) return;
        activity.runOnUiThread(() -> {
            if (!isAdded()) return;
            r.run();
        });
    }
}
