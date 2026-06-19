package com.zalexdev.stryker.routerscan;


import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.dashboard.Dashboard;
import com.zalexdev.stryker.routerscan.utils.RouterScanLog;
import com.zalexdev.stryker.utils.Core;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.apache.commons.net.util.SubnetUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressSeqRange;
import inet.ipaddr.IPAddressString;

public class RouterScanMain extends Fragment {


    public int timeout = 300;
    public String chroot;
    public Core core;
    public Context context;
    public RecyclerView mRecyclerView;
    public RouterAdapter adapter;
    public Activity activity;
    public TextView ranges_text;
    public TextView ports_text;
    public TextView rs_pinged;
    public TextView rs_ok;
    public TextView threads_text;
    public LinearProgressIndicator prog;
    public ArrayList<String> ipadresses = new ArrayList<>();
    public ArrayList<String> ports = new ArrayList<>();
    public int maximum = 3;
    public LinearProgressIndicator rsbar;
    public LinearProgressIndicator pingbar;
    public MaterialButton startbutton;

    private final BroadcastReceiver scanUpdates = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            refreshFromState();
        }
    };
    private boolean receiverRegistered;
    private boolean wasRunning;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View viewroot = inflater.inflate(R.layout.routerscan_fragment, container, false);
        context = getContext();
        activity = getActivity();
        core = new Core(context);
     
        

            if (!core.checkFile("/data/local/stryker/release/usr/bin/rs")){
                getParentFragmentManager().beginTransaction().replace(R.id.flContent, new Dashboard()).commit();
                core.toaster("RScan Not Installed!");
            }
            core.threadChrootCommand("apk add libcrypto1.1");
            core.threadChrootCommand("mkdir /sdcard/Stryker/rs");
            core.threadChrootCommand("cp /data/local/stryker/release/CORE/RS/* /sdcard/Stryker/rs");
            new Thread(() -> com.zalexdev.stryker.routerscan.utils.AuthLists.ensureDeployed(
                    getContext())).start();

        LinearLayout expand_toggle = viewroot.findViewById(R.id.router_toggle);
        View ranges_card = viewroot.findViewById(R.id.router_ranges_card);
        View ports_card = viewroot.findViewById(R.id.router_port_card);
        ranges_text = viewroot.findViewById(R.id.router_ranges_text);
        ports_text = viewroot.findViewById(R.id.router_ports);
        ImageView expand_toggle_img = viewroot.findViewById(R.id.router_toggle_img);
        View save = viewroot.findViewById(R.id.router_save);
        View terminalBtn = viewroot.findViewById(R.id.router_terminal);
        ExpandableLayout routerpanel = viewroot.findViewById(R.id.routerpanel_expand);
        startbutton = viewroot.findViewById(R.id.start_scanner);
        View setting_icon = viewroot.findViewById(R.id.router_settings_icon);
        threads_text = viewroot.findViewById(R.id.rs_threads);
        rs_ok = viewroot.findViewById(R.id.rs_ok);
        rs_pinged = viewroot.findViewById(R.id.rs_pinged);
        setting_icon.setOnClickListener(view -> settings());
        save.setOnClickListener(view -> {
            core.saveResult(RouterScanState.get().getGood());
            core.toaster(getString(R.string.saved_to));
        });
        terminalBtn.setOnClickListener(view -> openTerminal(null));
        rsbar = viewroot.findViewById(R.id.roterscan_progressbar);
        pingbar = viewroot.findViewById(R.id.ping_progressbar);
        mRecyclerView = viewroot.findViewById(R.id.routerscan_items);
        adapter = new RouterAdapter(context, activity, RouterScanState.get().results);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(adapter);
        restore();
        refreshFromState();

        expand_toggle_img.animate().rotation(180);
        expand_toggle.setOnClickListener(view -> {
            if (routerpanel.isExpanded()) {
                expand_toggle_img.animate().rotation(0);
            } else {
                expand_toggle_img.animate().rotation(180);
            }
            routerpanel.toggle();
        });
        ranges_card.setOnClickListener(view -> setranges());
        ranges_text.setOnClickListener(view -> setranges());
        ports_text.setOnClickListener(view -> setPorts());
        ports_card.setOnClickListener(view -> setPorts());

        startbutton.setOnClickListener(view -> {
            if (RouterScanState.get().running) {
                stopScan();
            } else if (ports_text.getText().toString().length() > 0
                    && ranges_text.getText().toString().length() > 0) {
                startScan();
            } else {
                core.toaster(context.getResources().getString(R.string.fill_rs));
            }
        });
        return viewroot;
    }

    private void startScan() {
        RouterScanLog.truncate(context);
        RouterScanLog.info(context, "scan",
                "batch start: " + ipadresses.size() + " ips × " + ports.size() + " ports");
        RouterScanState.get().resetForBatch(ipadresses, ports, maximum, timeout);
        wasRunning = true;
        adapter.notifyDataSetChanged();
        int total = Math.max(1, ipadresses.size() * ports.size());
        pingbar.setMax(total);
        rsbar.setMax(total);
        ContextCompat.startForegroundService(context,
                new Intent(context, RouterScanService.class).setAction(RouterScanService.ACTION_START));
        showRunning(true);
    }

    private void stopScan() {
        context.startService(new Intent(context, RouterScanService.class)
                .setAction(RouterScanService.ACTION_STOP));
        showRunning(false);
    }

    private void showRunning(boolean running) {
        startbutton.setText(running ? R.string.stop : R.string.start);
        startbutton.setIcon(context.getDrawable(running ? R.drawable.close : R.drawable.run));
    }

    private void refreshFromState() {
        if (adapter == null || activity == null) return;
        activity.runOnUiThread(() -> {
            RouterScanState st = RouterScanState.get();
            adapter.notifyDataSetChanged();
            rs_pinged.setText(String.valueOf(st.getResponsive()));
            rs_ok.setText(String.valueOf(st.getSuccess()));
            threads_text.setText(maximum + " (" + st.getCompleted() + "/" + st.total + ")");
            if (st.total > 0) {
                pingbar.setMax(st.total);
                rsbar.setMax(st.total);
            }
            setBar(pingbar, st.getCompleted());
            setBar(rsbar, st.getResponsive());
            showRunning(st.running);
            if (!st.running && wasRunning) {
                wasRunning = false;
                rsfinish();
            }
        });
    }

    private void setBar(LinearProgressIndicator bar, int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bar.setProgress(value, true);
        } else {
            bar.setProgress(value);
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(RouterScanService.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanUpdates, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(scanUpdates, f);
        }
        receiverRegistered = true;
        refreshFromState();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (receiverRegistered) {
            try { context.unregisterReceiver(scanUpdates); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
    }

    public void getipsbyrange(String lowerStr, String upperStr) {
        try {
            IPAddress lower = new IPAddressString(lowerStr).toAddress();
            IPAddress upper = new IPAddressString(upperStr).toAddress();
            IPAddressSeqRange range = lower.toSequentialRange(upper);
            for (IPAddress addr : range.getIterable()) {
                ipadresses.add(String.valueOf(addr));
            }
        } catch (AddressStringException e) {
            e.printStackTrace();
        }
    }

    private void setranges() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.router_setrange);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView cancel = dialog.findViewById(R.id.rs_cancel);
        TextView ok = dialog.findViewById(R.id.rs_ok);
        TextInputLayout input = dialog.findViewById(R.id.textField);
        StringBuilder seed = new StringBuilder();
        for (String r : core.getListString("restore_ranges")) seed.append(r);
        Objects.requireNonNull(input.getEditText()).setText(seed.toString());
        cancel.setOnClickListener(view -> dialog.dismiss());
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {

            for (int i = start;i < end;i++) {
                if (!Character.isDigit(source.charAt(i)) && !Character.toString(source.charAt(i)).equals(".") && !Character.toString(source.charAt(i)).equals("/") && !Character.toString(source.charAt(i)).equals("-") && !Character.toString(source.charAt(i)).equals("\n"))
                {
                    return "";
                }
            }
            return null;
        };
        input.getEditText().setFilters(new InputFilter[]{filter});
        ok.setOnClickListener(view -> {
            String ipstring = String.valueOf(Objects.requireNonNull(input.getEditText()).getText());
            List<String> iplist = Arrays.asList(ipstring.split("\n"));
            ArrayList<String> r = new ArrayList<>();
            ipadresses = new ArrayList<>();
            ranges_text.setText("");
            try {


            for (int i = 0; i < iplist.size(); i++) {

                if (iplist.get(i).contains("-")) {
                    List<String> range = Arrays.asList(iplist.get(i).split("-"));
                    if (validate(range.get(0)) && validate(range.get(1))) {
                        getipsbyrange(range.get(0), range.get(1));
                        ranges_text.append(iplist.get(i) + "\n");
                        r.add(iplist.get(i) + "\n");
                    }
                } else if (iplist.get(i).contains("/")) {
                    getipbysubnet(iplist.get(i));
                    ranges_text.append(iplist.get(i) + "\n");
                    r.add(iplist.get(i) + "\n");
                } else if (validate(iplist.get(i))) {
                    ipadresses.add(iplist.get(i));
                    ranges_text.append(iplist.get(i) + "\n");
                    r.add(iplist.get(i) + "\n");
                }
            }
            }catch(Exception e){
                core.toaster("Invalid ranges or ports!");
            }
            core.putListString("restore_ips", ipadresses);
            core.putListString("restore_ranges", r);
            dialog.dismiss();
        });
        dialog.show();
    }
    private void setPorts() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.router_setrange);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView cancel = dialog.findViewById(R.id.rs_cancel);
        TextView ok = dialog.findViewById(R.id.rs_ok);
        TextView title = dialog.findViewById(R.id.title);
        title.setText("Set ports");

        TextInputLayout input = dialog.findViewById(R.id.textField);
        StringBuilder seedPorts = new StringBuilder();
        for (String p : core.getListString("restore_ports")) seedPorts.append(p).append('\n');
        Objects.requireNonNull(input.getEditText()).setText(seedPorts.toString().trim());
        cancel.setOnClickListener(view -> dialog.dismiss());
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {

            for (int i = start;i < end;i++) {
                if (!Character.isDigit(source.charAt(i)) && !Character.toString(source.charAt(i)).equals("\n"))
                {
                    return "";
                }
            }
            return null;
        };
        input.setHint("Enter ports");
        input.getEditText().setFilters(new InputFilter[]{filter});
        ok.setOnClickListener(view -> {
            ports_text.setText("");
            List<String> tempports =  Arrays.asList(String.valueOf(Objects.requireNonNull(input.getEditText()).getText()).split("\n"));
            ports = new ArrayList<>();
            ports.addAll(tempports);
            core.putListString("restore_ports",ports);
            for (String p : ports){
                ports_text.append(p+"\n");
            }
            dialog.dismiss();
        });
        dialog.show();
    }
    private void settings() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.router_settings);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView ok = dialog.findViewById(R.id.save_router_settings);
        TextInputLayout maxtreads = dialog.findViewById(R.id.setmaxthreads);
        TextInputLayout time = dialog.findViewById(R.id.setmaxtimeout);
        Objects.requireNonNull(maxtreads.getEditText()).setText(String.valueOf(maximum));
        Objects.requireNonNull(time.getEditText()).setText(String.valueOf(timeout));
        ok.setOnClickListener(view -> {
            try {


            maximum = Integer.parseInt(String.valueOf(maxtreads.getEditText().getText()));
            timeout = Integer.parseInt(String.valueOf(time.getEditText().getText()));
            core.putInt("restore_maximum", maximum);
            core.putInt("restore_timeout", timeout);}
            catch (Exception e){
                core.toaster("Invalid values!");
            }
            dialog.dismiss();
        });
        dialog.show();

    }

    public void getipbysubnet(String ipOrCidr) {
        SubnetUtils utils = new SubnetUtils(ipOrCidr.replaceAll("\\s+",""));
        String[] allIps = utils.getInfo().getAllAddresses();
        Collections.addAll(ipadresses, allIps);
    }

    public void restore() {
        ipadresses = core.getListString("restore_ips");
        ArrayList<String> ip = core.getListString("restore_ranges");
        ports = core.getListString("restore_ports");
        timeout = core.getInt("restore_timeout");
        maximum = core.getInt("restore_maximum");
        if (timeout == 0) timeout = 300;
        if (maximum == 0) maximum = 50;
        ranges_text.setText("");
        ports_text.setText("");
        if (ip.isEmpty()) {
            ranges_text.setText(R.string.tap_to_set_ranges);
        } else {
            for (String i2 : ip) ranges_text.append(i2);
        }
        if (ports.isEmpty()) {
            ports_text.setText(R.string.tap_to_set_ports);
        } else {
            for (String port : ports) ports_text.append(port + "\n");
        }
    }



    public void settext(String text, TextView output) {
        activity.runOnUiThread(() -> output.setText(text));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

    }

    public void setProg(LinearProgressIndicator progressIndicator, int prog) {
        activity.runOnUiThread(() -> {
            progressIndicator.setVisibility(View.INVISIBLE);
            progressIndicator.setIndeterminate(false);
            progressIndicator.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {progressIndicator.setProgress(prog, true);}});
    }



    public boolean validate(final String ip) {
        String PATTERN = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
        return ip.matches(PATTERN);
    }

    public void rsfinish() {
        RouterScanState st = RouterScanState.get();
        RouterScanLog.info(context, "scan",
                "batch finished — " + st.getSuccess() + "/" + st.getResponsive() + " cracked");
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.scan_finised)
                .setMessage(R.string.scan_finished_desc)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {})
                .setNeutralButton(R.string.save, (dialogInterface, i) -> {
                    core.saveResult(st.getGood());
                    core.toaster(getString(R.string.saved_to));
                })
                .setNegativeButton("Terminal", (dialogInterface, i) -> openTerminal(null))
                .show();
    }

    public void openTerminal(String host) {
        if (getActivity() == null) return;
        RouterScanTerminal terminal = host != null
                ? RouterScanTerminal.forHost(host)
                : RouterScanTerminal.newInstance();
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContent, terminal)
                .addToBackStack(null)
                .commit();
    }

    public Router rs_result(ArrayList<String> output) {
        Router result = new Router();
        result.setSuccess(false);

        for (int i = 0; i < output.size(); i++) {
            String temp = output.get(i);
            if (temp.contains("SSID:") && !temp.contains("BSSID:")) {
                String ssid = temp.replace("SSID: ", "");
                result.setSsid(ssid);
                result.setSuccess(true);
            } else if (temp.contains("Auth:")) {
                String auth = temp.replace("Auth: ", "");
                result.setAuth(auth);
                result.setSuccess(true);
            } else if (temp.contains("Key:")) {
                String pswd = temp.replace("Key: ", "");
                result.setPsk(pswd);
                result.setSuccess(true);
            } else if (temp.contains("WPS:")) {
                String wps = temp.replace("WPS: ", "");
                result.setWps(wps);
                result.setSuccess(true);
            } else if (temp.contains("Title:")) {
                String title = temp.replace("Title: ", "");
                result.setTitle(title);
            } else if (temp.contains("BSSID:")){
                String mac = temp.replace("BSSID: ","");
                result.setBssid(mac);
            }
            if (result.getSuccess()) {
                result.setStatus("Success");
                result.setType(1);
            }
        }
        return result;
    }
}
