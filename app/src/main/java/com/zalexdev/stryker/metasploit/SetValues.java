package com.zalexdev.stryker.metasploit;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.MsfExploit;
import com.zalexdev.stryker.dashboard.Dashboard;
import com.zalexdev.stryker.metasploit.adapters.ValueAdapter;
import com.zalexdev.stryker.metasploit.utils.MetasploitUtils;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SetValues extends Fragment {

    public Core core;
    public Activity activity;
    public Context context;
    public ValueAdapter adapter;
    public MetasploitUtils metasploitUtils;
    public RecyclerView recyclerView;
    public MsfExploit msfExploit;
    public String ip;
    public ArrayList<String> ports;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private TextView title;
    private TextView description;
    private TextView reference;
    private MaterialButton launch;
    private MaterialButton back;
    private LinearProgressIndicator progress;
    private TextView subtitle;

    public SetValues() {
    }

    public SetValues(MsfExploit exploit) {
        msfExploit = exploit;
    }

    public SetValues(MsfExploit exploit, String ip, ArrayList<String> ports) {
        msfExploit = exploit;
        this.ip = ip;
        this.ports = ports;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.setvalues_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        context = getContext();
        activity = getActivity();
        core = new Core(context);
        FragmentManager fm = getParentFragmentManager();

        if (msfExploit == null) {
            fm.beginTransaction().replace(R.id.flContent, new Dashboard()).commit();
            return;
        }

        metasploitUtils = ((MainActivity) activity).getMetasploitUtils();

        title = view.findViewById(R.id.exploit_title);
        description = view.findViewById(R.id.exploit_description);
        reference = view.findViewById(R.id.exploit_reference);
        launch = view.findViewById(R.id.launch_button);
        back = view.findViewById(R.id.back_button);
        progress = view.findViewById(R.id.values_progress);
        subtitle = view.findViewById(R.id.values_subtitle);
        recyclerView = view.findViewById(R.id.values_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        back.setOnClickListener(v -> fm.beginTransaction()
                .setCustomAnimations(R.anim.nav_fade_enter, R.anim.nav_fade_exit)
                .replace(R.id.flContent, new Metasploit()).commit());

        title.setText(msfExploit.getTitle());
        subtitle.setText(R.string.msf_values_loading);
        launch.setEnabled(false);

        cancelled.set(false);
        new Thread(() -> {
            msfExploit = metasploitUtils.getArguments(msfExploit);
            ArrayList<com.zalexdev.stryker.custom.Argument> args = msfExploit.getArguments();
            if (cancelled.get() || activity == null || !isAdded()) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (cancelled.get() || activity == null || !isAdded()) {
                    return;
                }
                title.setText(msfExploit.getTitle());
                subtitle.setText(R.string.msf_values_subtitle);
                if (msfExploit.getDescription() != null && !msfExploit.getDescription().isEmpty()) {
                    description.setText(msfExploit.getDescription());
                    description.setVisibility(View.VISIBLE);
                }
                if (msfExploit.getReference() != null && !msfExploit.getReference().isEmpty()) {
                    reference.setText(msfExploit.getReference());
                    reference.setVisibility(View.VISIBLE);
                }
                adapter = new ValueAdapter(context, activity, msfExploit, recyclerView);
                adapter.ip = ip;
                adapter.ports = ports;
                recyclerView.setAdapter(adapter);
                recyclerView.setItemViewCacheSize(50);
                progress.setVisibility(View.GONE);
                launch.setEnabled(true);
                launch.setOnClickListener(v -> {
                    if (adapter.isAllValuesSet() && adapter.getValues().size() > 0) {
                        msfExploit.setArguments(adapter.getValues());
                        fm.beginTransaction()
                                .setCustomAnimations(R.anim.nav_fade_enter, R.anim.nav_fade_exit)
                                .replace(R.id.flContent, new ExploitLaucnher(msfExploit)).commit();
                    } else {
                        Toast.makeText(context, R.string.msf_values_missing_required, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }, "msf-args").start();
    }

    @Override
    public void onDestroyView() {
        cancelled.set(true);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        cancelled.set(true);
        super.onDestroy();
    }
}
