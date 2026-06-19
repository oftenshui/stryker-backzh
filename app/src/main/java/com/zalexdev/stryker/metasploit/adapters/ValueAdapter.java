package com.zalexdev.stryker.metasploit.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Argument;
import com.zalexdev.stryker.custom.MsfExploit;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;

public class ValueAdapter extends RecyclerView.Adapter<ValueAdapter.ViewHolder> {

    public MsfExploit msfExploit;
    public Context context;
    public Activity activity;
    public Core core;
    public ArrayList<Argument> arguments;
    public ArrayList<AutoCompleteTextView> autoCompleteTextViews = new ArrayList<>();
    public String[] values;
    public String ip;
    public ArrayList<String> ports;
    public RecyclerView rv;

    public ValueAdapter(Context context, Activity activity, MsfExploit exploit, RecyclerView rv) {
        this.context = context;
        this.msfExploit = exploit;
        this.activity = activity;
        this.core = new Core(context);
        this.arguments = msfExploit.getArguments();
        this.values = new String[arguments.size()];
        this.rv = rv;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.exploit_variable_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, @SuppressLint("RecyclerView") int position) {
        Argument argument = arguments.get(position);
        h.name.setText(argument.getName());
        h.requiredChip.setVisibility(argument.isRequired() ? View.VISIBLE : View.GONE);
        h.description.setText(argument.getDescription());
        h.value.setText(argument.getValue());

        if (Core.isNumeric(argument.getValue()) && (argument.getName().equals("RPORT")
                || argument.getName().equals("LPORT") || argument.getName().equals("THREADS"))) {
            h.value.setInputType(InputType.TYPE_CLASS_NUMBER);
            if (ports != null) {
                h.value.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_dropdown_item_1line, ports));
            }
        } else if (argument.getValue() != null
                && (argument.getValue().contains("true") || argument.getValue().contains("false"))) {
            h.value.setInputType(InputType.TYPE_NULL);
            ArrayList<String> bools = new ArrayList<>();
            bools.add("true");
            bools.add("false");
            h.value.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, bools));
        } else {
            h.value.setInputType(InputType.TYPE_CLASS_TEXT);
            h.value.setAdapter(null);
        }

        if (argument.getName().equals("LHOST") || argument.getName().equals("LHOSTS")) {
            if (h.value.getText().length() == 0) h.value.setText(core.getLocalIpaddress());
        } else if (argument.getName().equals("RHOSTS") || argument.getName().equals("RHOST")) {
            arguments.get(position).setAuto(core.getLatestIps());
            if (ip != null && h.value.getText().length() == 0) h.value.setText(ip);
        }
        if (argument.getAuto().size() > 0) {
            h.value.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, argument.getAuto()));
            h.value.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        if (!autoCompleteTextViews.contains(h.value)) autoCompleteTextViews.add(h.value);
    }

    @Override
    public int getItemCount() {
        return arguments.size();
    }

    @Override
    public long getItemId(int position) {
        return arguments.get(position).getName().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView description;
        public TextView requiredChip;
        public AutoCompleteTextView value;

        public ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            description = v.findViewById(R.id.description);
            requiredChip = v.findViewById(R.id.required_chip);
            value = v.findViewById(R.id.value_layout);
        }
    }

    public boolean isAllValuesSet() {
        updateValues();
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).isRequired()) {
                if (values[i] == null || values[i].equals("")) return false;
            }
        }
        return true;
    }

    public void updateValues() {
        if (autoCompleteTextViews.size() != arguments.size()) {
            rv.smoothScrollToPosition(getItemCount() - 1);
        }
        for (int i = 0; i < arguments.size() && i < autoCompleteTextViews.size(); i++) {
            values[i] = autoCompleteTextViews.get(i).getText().toString();
        }
    }

    public ArrayList<Argument> getValues() {
        ArrayList<Argument> out = new ArrayList<>();
        for (int i = 0; i < this.arguments.size(); i++) {
            Argument argument = this.arguments.get(i);
            if (i < values.length && values[i] != null && !values[i].equals("")) {
                argument.setValue(values[i]);
            }
            out.add(argument);
        }
        return out;
    }
}
