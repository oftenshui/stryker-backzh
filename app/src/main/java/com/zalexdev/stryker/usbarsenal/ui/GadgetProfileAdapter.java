package com.zalexdev.stryker.usbarsenal.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.hid.configfs.GadgetFunction;
import com.zalexdev.stryker.hid.configfs.GadgetProfile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GadgetProfileAdapter extends RecyclerView.Adapter<GadgetProfileAdapter.ViewHolder> {

    public interface Callbacks {
        void onApply(@NonNull GadgetProfile profile);
        void onEdit(@NonNull GadgetProfile profile);
        void onDelete(@NonNull GadgetProfile profile);
    }

    private final Callbacks callbacks;
    private final List<GadgetProfile> items = new ArrayList<>();

    public GadgetProfileAdapter(@NonNull Callbacks callbacks) {
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    public void submit(@NonNull List<GadgetProfile> profiles) {
        items.clear();
        items.addAll(profiles);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) { return items.get(position).id; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gadget_profile, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        GadgetProfile p = items.get(pos);
        h.name.setText(p.name);
        h.os.setText(p.targetOs.name());
        h.fns.setText(formatFunctions(p));
        h.id.setText(String.format("%s:%s — %s / %s",
                p.idVendor, p.idProduct, p.manufacturer, p.productName));
        h.apply.setOnClickListener(v -> callbacks.onApply(p));
        h.edit.setOnClickListener(v -> callbacks.onEdit(p));
        h.delete.setOnClickListener(v -> callbacks.onDelete(p));
    }

    @Override public int getItemCount() { return items.size(); }

    private static String formatFunctions(GadgetProfile p) {
        StringBuilder sb = new StringBuilder();
        Iterator<GadgetFunction> it = p.functions.iterator();
        while (it.hasNext()) {
            sb.append(it.next().key);
            if (it.hasNext()) sb.append(" · ");
        }
        return sb.length() == 0 ? "no functions" : sb.toString();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView name;
        final MaterialTextView os;
        final MaterialTextView fns;
        final MaterialTextView id;
        final View apply;
        final View edit;
        final View delete;

        ViewHolder(@NonNull View v) {
            super(v);
            name   = v.findViewById(R.id.gp_name);
            os     = v.findViewById(R.id.gp_os_pill);
            fns    = v.findViewById(R.id.gp_functions);
            id     = v.findViewById(R.id.gp_identity);
            apply  = v.findViewById(R.id.gp_apply_btn);
            edit   = v.findViewById(R.id.gp_edit_btn);
            delete = v.findViewById(R.id.gp_delete_btn);
        }
    }
}
