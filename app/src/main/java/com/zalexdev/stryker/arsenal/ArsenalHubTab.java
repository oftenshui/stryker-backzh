package com.zalexdev.stryker.arsenal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Exploit;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.List;

public class ArsenalHubTab extends Fragment {

    private Activity activity;
    private Context context;
    private Core core;

    private RecyclerView list;
    private View empty;
    private TextView countChip;
    private ExtendedFloatingActionButton add;

    private final List<Exploit> data = new ArrayList<>();
    private HubAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_arsenal_hub, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);

        list = view.findViewById(R.id.hub_list);
        empty = view.findViewById(R.id.hub_empty);
        countChip = view.findViewById(R.id.hub_count_chip);
        add = view.findViewById(R.id.hub_add);

        list.setLayoutManager(new LinearLayoutManager(context));
        list.setItemViewCacheSize(64);
        adapter = new HubAdapter(context, data, (exploit, position) -> openRunSheet(exploit, position));
        list.setAdapter(adapter);

        add.setOnClickListener(v -> openAddSheet(null));

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    void reload() {
        if (core == null) return;
        data.clear();
        data.addAll(core.getExploits());
        adapter.notifyDataSetChanged();
        countChip.setText(String.valueOf(data.size()));
        boolean isEmpty = data.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        notifyHostSubtitle();
    }

    private void notifyHostSubtitle() {
        Fragment parent = getParentFragment();
        if (parent instanceof ArsenalFragment) {
            ((ArsenalFragment) parent).refreshSubtitle();
        }
    }

    private void openAddSheet(@Nullable Exploit prefill) {
        AddExploitSheet sheet = AddExploitSheet.newInstance(prefill, () -> {
            reload();
        });
        sheet.show(getChildFragmentManager(), "arsenal_add_sheet");
    }

    private void openRunSheet(Exploit exploit, int position) {
        RunExploitSheet sheet = RunExploitSheet.newInstance(exploit, position, new RunExploitSheet.Callback() {
            @Override
            public void onDeleted(int pos) {
                if (pos >= 0 && pos < data.size()) {
                    core.deleteExploit(pos);
                    data.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    countChip.setText(String.valueOf(data.size()));
                    boolean isEmpty = data.isEmpty();
                    empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    notifyHostSubtitle();
                }
            }
        });
        sheet.show(getChildFragmentManager(), "arsenal_run_sheet");
    }
}
