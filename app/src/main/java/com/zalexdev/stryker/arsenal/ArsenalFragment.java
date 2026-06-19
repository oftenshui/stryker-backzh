package com.zalexdev.stryker.arsenal;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

public class ArsenalFragment extends Fragment {

    public static final String ARG_START_TAB = "arsenal_start_tab";
    public static final String TAB_HUB = "hub";
    public static final String TAB_DB = "db";

    private static final String PREF_LAST_TAB = "arsenal_last_tab";

    private final MainActivity.Receiver receiver = new MainActivity.Receiver();

    private Activity activity;
    private Context context;
    private Core core;

    private LinearLayout tabHub;
    private LinearLayout tabDb;
    private TextView tabHubText;
    private TextView tabDbText;
    private ImageView tabHubIcon;
    private ImageView tabDbIcon;

    private TextView subtitle;
    private String currentTab = TAB_HUB;

    public static ArsenalFragment forTab(String tab) {
        ArsenalFragment f = new ArsenalFragment();
        Bundle args = new Bundle();
        args.putString(ARG_START_TAB, tab);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_arsenal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);

        receiver.setTitle(getString(R.string.arsenal_title));

        subtitle = view.findViewById(R.id.arsenal_subtitle);
        tabHub = view.findViewById(R.id.arsenal_tab_hub);
        tabDb = view.findViewById(R.id.arsenal_tab_db);
        tabHubText = view.findViewById(R.id.arsenal_tab_hub_text);
        tabDbText = view.findViewById(R.id.arsenal_tab_db_text);
        tabHubIcon = view.findViewById(R.id.arsenal_tab_hub_icon);
        tabDbIcon = view.findViewById(R.id.arsenal_tab_db_icon);

        tabHub.setOnClickListener(v -> showTab(TAB_HUB));
        tabDb.setOnClickListener(v -> showTab(TAB_DB));

        String startTab;
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_START_TAB)) {
            startTab = args.getString(ARG_START_TAB, TAB_HUB);
        } else {
            startTab = core.getString(PREF_LAST_TAB);
        }
        if (!TAB_DB.equals(startTab)) {
            startTab = TAB_HUB;
        }
        showTab(startTab);
        refreshSubtitle();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSubtitle();
    }

    @Override
    public void onDestroy() {
        receiver.restoreTitle();
        super.onDestroy();
    }

    void refreshSubtitle() {
        if (subtitle == null || core == null) return;
        int count = core.getExploits().size();
        boolean dbInstalled = core.checkFile("/data/local/stryker/release/exploitdb/searchsploit");
        String installState = dbInstalled
                ? getString(R.string.arsenal_subtitle_db_ready)
                : getString(R.string.arsenal_subtitle_db_missing);
        subtitle.setText(getString(R.string.arsenal_subtitle_format, count, installState));
    }

    private void showTab(String tab) {
        currentTab = tab;
        if (core != null) {
            core.putString(PREF_LAST_TAB, TAB_DB.equals(tab) ? TAB_DB : TAB_HUB);
        }
        Fragment child = TAB_DB.equals(tab) ? new ArsenalDatabaseTab() : new ArsenalHubTab();
        getChildFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.arsenal_tab_container, child, "arsenal_tab_" + tab)
                .commit();
        styleTabs();
    }

    private void styleTabs() {
        boolean hub = TAB_HUB.equals(currentTab);
        if (hub) {
            tabHub.setBackgroundResource(R.drawable.arsenal_tab_selected);
            tabDb.setBackgroundResource(R.drawable.arsenal_tab_unselected);
            tabHubText.setTextColor(ContextCompat.getColor(context, R.color.night_contrast));
            tabDbText.setTextColor(ContextCompat.getColor(context, R.color.grey));
            tabHubIcon.setColorFilter(ContextCompat.getColor(context, R.color.stryker_accent));
            tabDbIcon.setColorFilter(ContextCompat.getColor(context, R.color.grey));
            tabHubText.setTypeface(null, android.graphics.Typeface.BOLD);
            tabDbText.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            tabHub.setBackgroundResource(R.drawable.arsenal_tab_unselected);
            tabDb.setBackgroundResource(R.drawable.arsenal_tab_selected);
            tabHubText.setTextColor(ContextCompat.getColor(context, R.color.grey));
            tabDbText.setTextColor(ContextCompat.getColor(context, R.color.night_contrast));
            tabHubIcon.setColorFilter(ContextCompat.getColor(context, R.color.grey));
            tabDbIcon.setColorFilter(ContextCompat.getColor(context, R.color.stryker_accent));
            tabHubText.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabDbText.setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

    public void switchTo(String tab) {
        showTab(tab);
    }
}
