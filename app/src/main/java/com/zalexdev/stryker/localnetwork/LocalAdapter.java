package com.zalexdev.stryker.localnetwork;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.Exploit;
import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.exploithub.utils.BasicExploitLaunch;
import com.zalexdev.stryker.localnetwork.exploits.RouterScan;
import com.zalexdev.stryker.localnetwork.utils.NmapReportGenerator;
import com.zalexdev.stryker.metasploit.InstallMetasploit;
import com.zalexdev.stryker.utils.AdvancedProcess;
import com.zalexdev.stryker.utils.AdvancedProcessList;
import com.zalexdev.stryker.utils.Core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;


public class LocalAdapter extends RecyclerView.Adapter<LocalAdapter.ViewHolder> {
    public Context context;
    public Activity activity;
    public String port;
    public String portcustom = "";
    public ArrayList<Device> devices;
    public Core core;
    public Dialog dialog = null;
    public String dialogip = "";
    public boolean hotspot = false;
    public String venomlist = "windows apk powershell python linux php perl tomcat osx java bash asp aspx";


    public LocalAdapter(Context context2, Activity mActivity, ArrayList<Device> devs) {
        context = context2;
        activity = mActivity;
        devices = devs;
        core = new Core(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.local_item, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint({"UseCompatLoadingForDrawables", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder adapter, @SuppressLint("RecyclerView") final int position) {
        Device device = devices.get(position);
        String gateway = getGateway();
        String ip = device.getIp();
        String mac = device.getMac();

        adapter.local_ip.setText(ip);
        adapter.local_manufacture.setText(core.getVendorByMacFromDB(device.getMac()));

        if (device.getSubname() != null && !device.getSubname().isEmpty()
                && !device.getSubname().equals(device.getIp())) {
            String model = core.getDeviceByCodeNameFromDB(device.getSubname());
            if (model.length() > 2) {
                device.setVendor(model);
                adapter.local_manufacture.setText(model);
                device.setOs("Android");
            }
            adapter.local_ip.setText(device.getIp() + " (" + device.getSubname() + ")");
        }

        if (!core.getBoolean("hide")) {
            adapter.local_mac.setText(mac);
        } else {
            adapter.local_mac.setText(Core.HIDDEN_MAC);
        }

        if (device.isShim()) {
            adapter.shim.showShimmer(true);
        } else {
            adapter.shim.hideShimmer();
            if (dialog != null && dialog.isShowing() && device.getIp().equals(dialogip)) {
                dialog.dismiss();
                newDialog(device, position);
            }
        }

        adapter.img_1.setVisibility(View.GONE);
        adapter.img_2.setVisibility(View.GONE);
        adapter.img_3.setVisibility(View.GONE);
        adapter.img_4.setVisibility(View.GONE);
        adapter.img_5.setVisibility(View.GONE);

        ArrayList<String> portStrings = device.portsToString();
        if (portStrings.contains("80") || portStrings.contains("443")
                || portStrings.contains("8080") || portStrings.contains("8000")) {
            adapter.img_1.setVisibility(View.VISIBLE);
            adapter.img_1.setImageDrawable(context.getDrawable(R.drawable.web));
        }
        if (portStrings.contains("445") || portStrings.contains("21")) {
            adapter.img_2.setVisibility(View.VISIBLE);
            adapter.img_2.setImageDrawable(context.getDrawable(R.drawable.folder));
        }
        if (portStrings.contains("22")) {
            adapter.img_3.setVisibility(View.VISIBLE);
            adapter.img_3.setImageDrawable(context.getDrawable(R.drawable.terminal));
        }

        if (device.getOs().equals("Unknown")) {
            device.guessos();
        }

        adapter.local_img.setImageDrawable(context.getDrawable(device.getImage()));
        if (device.getIp().equals(gateway)) {
            adapter.local_img.setImageDrawable(context.getDrawable(R.drawable.router));
        }
        int tint = iconTintForDevice(device, gateway);
        adapter.local_img.setColorFilter(tint);

        adapter.local_ports.setText(String.valueOf(portStrings.size()));

        adapter.card.setOnClickListener(view -> newDialog(device, position));
        if (!device.isShim()) {
            adapter.local_ports.setOnClickListener(view -> showPorts2(device));
            adapter.ports.setOnClickListener(view -> showPorts2(device));
        } else {
            adapter.local_ports.setOnClickListener(view -> core.toaster(
                    context.getString(R.string.local_wait_scan)));
            adapter.ports.setOnClickListener(view -> core.toaster(
                    context.getString(R.string.local_wait_scan)));
        }

        adapter.divider.setVisibility(position == devices.size() - 1 ? View.GONE : View.VISIBLE);
    }

    private int iconTintForDevice(Device device, String gateway) {
        if (device.isIscutted()) {
            return ContextCompat.getColor(context, R.color.red);
        }
        if (device.getIp().equals(gateway)) {
            return Color.parseColor("#AB47BC");
        }
        String os = device.getOs() == null ? "" : device.getOs().toLowerCase(Locale.ROOT);
        if (os.contains("windows")) return Color.parseColor("#1565C0");
        if (os.contains("linux")) return Color.parseColor("#EF6C00");
        if (os.contains("mac") || os.contains("ios") || os.contains("apple"))
            return Color.parseColor("#5E35B1");
        if (os.contains("android")) return Color.parseColor("#2E7D32");
        if (os.contains("camera")) return Color.parseColor("#00897B");
        if (os.contains("printer")) return Color.parseColor("#3949AB");
        return ContextCompat.getColor(context, R.color.grey);
    }

    public void netcutdialog(Device d, int pos) {
        if (pos != 0) {
            String[] types = new String[]{
                    context.getString(R.string.cut_dev),
                    context.getString(R.string.perm_cut),
                    context.getString(R.string.cut20),
                    context.getString(R.string.local_netcut_restore)
            };
            new MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.local_netcut_choose))
                    .setItems(types, (dialogInterface, i) -> {
                        if (dialog != null) dialog.dismiss();
                        String target = d.getIp();
                        String gw = devices.get(0).getIp();
                        String cmd = "";
                        if (i == 0) {
                            core.getLogger().writeLine("Cutting network connection to " + target + " from " + gw, 1);
                            cmd = " python3 /CORE/MegaCut/megacut.py " + target + " " + gw + " -k";
                        } else if (i == 1) {
                            core.getLogger().writeLine("HARD cutting network connection to " + target + " from " + gw, 1);
                            cmd = " python3 /CORE/MegaCut/megacut.py " + target + " " + gw + " -m";
                        } else if (i == 2) {
                            core.getLogger().writeLine("Cutting network connection (20s) to " + target + " from " + gw, 1);
                            cmd = " python3 /CORE/MegaCut/megacut.py " + target + " " + gw + " -b";
                        } else {
                            core.getLogger().writeLine("Enabling connection to " + target + " from " + gw, 1);
                            cmd = " python3 /CORE/MegaCut/megacut.py " + target + " " + gw + " -r";
                        }
                        String finalCmd = cmd;
                        new Thread(() -> core.customChrootCommand(finalCmd)).start();
                        d.setIscutted(i != 3);
                        notifyItemChanged(pos);
                        if (i == 2) {
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new TimerTask() {
                                @Override
                                public void run() {
                                    d.setIscutted(false);
                                    activity.runOnUiThread(() -> notifyItemChanged(pos));
                                    timer.cancel();
                                }
                            }, 20000, 20000);

                        }
                    })
                    .show();
        } else if (!devices.get(0).isIscutted()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.local_netcut_confirm_all_title)
                    .setMessage(R.string.local_netcut_confirm_all_body)
                    .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                        StringBuilder cmd = new StringBuilder("python3 /CORE/MegaCut/megacut.py ");
                        d.setIscutted(true);
                        boolean first = true;
                        for (Device d2 : devices) {
                            if (!d2.getIp().equals(devices.get(0).getIp())) {
                                if (first) {
                                    cmd.append(d2.getIp());
                                    first = false;
                                } else {
                                    cmd.append(",").append(d2.getIp());
                                }
                                d2.setIscutted(true);
                            }
                            notifyItemChanged(devices.indexOf(d2));
                        }
                        cmd.append(" ").append(devices.get(0).getIp()).append(" -k");
                        new Thread(() -> core.customChrootCommand(cmd.toString())).start();
                        if (dialog != null) dialog.dismiss();
                    })
                    .setNegativeButton(R.string.no, (di, i) -> di.dismiss()).show();
        } else {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.local_netcut_restore_all_title)
                    .setMessage(R.string.local_netcut_restore_all_body)
                    .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                        StringBuilder cmd = new StringBuilder("python3 /CORE/MegaCut/megacut.py ");
                        d.setIscutted(false);
                        boolean first = true;
                        for (Device d2 : devices) {
                            if (!d2.getIp().equals(devices.get(0).getIp())) {
                                if (first) {
                                    cmd.append(d2.getIp());
                                    first = false;
                                } else {
                                    cmd.append(",").append(d2.getIp());
                                }
                                d2.setIscutted(false);
                            }
                            notifyItemChanged(devices.indexOf(d2));
                        }
                        cmd.append(" ").append(devices.get(0).getIp()).append(" -r");
                        new Thread(() -> core.customChrootCommand(cmd.toString())).start();
                        if (dialog != null) dialog.dismiss();
                    })
                    .setNegativeButton(R.string.no, (di, i) -> di.dismiss()).show();
        }
    }

    private void runexploits(ArrayList<Exploit> exploits) {
        if (exploits == null || exploits.isEmpty()) return;
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.exploit_dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        LinearProgressIndicator prog = dialog.findViewById(R.id.exploit_prog);
        TextView subtitle = dialog.findViewById(R.id.exploit_title);
        ProgressBar headerSpinner = dialog.findViewById(R.id.circle_progress);
        ImageView headerIcon = dialog.findViewById(R.id.exploit_header_icon);
        TextView terminal = dialog.findViewById(R.id.exploit_terminal);
        android.widget.ScrollView terminalScroll = dialog.findViewById(R.id.exploit_terminal_scroll);
        TextView cancel = dialog.findViewById(R.id.exploit_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(false);
        terminal.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        final int total = exploits.size();
        final int[] done = {0};
        final int[] success = {0};
        final java.util.List<String> hits = java.util.Collections.synchronizedList(new ArrayList<>());

        setText(subtitle, context.getString(R.string.arsenal_run_batch_summary_running, 0, total));
        activity.runOnUiThread(() -> {
            prog.setIndeterminate(false);
            prog.setMax(total);
            prog.setProgress(0);
        });

        for (Exploit exploit : exploits) {
            new Thread(() -> {
                activity.runOnUiThread(() -> {
                    subtitle.setText(context.getString(R.string.arsenal_run_batch_testing, exploit.getTitle()));
                    terminal.append("\n── " + exploit.getTitle() + " ──\n");
                    terminal.append("$ " + exploit.genereteLaunchCommand() + "\n");
                    autoScrollTerminal(terminal, terminalScroll);
                });
                boolean result = false;
                try {
                    result = new BasicExploitLaunch(exploit.getSuccesspatern(), exploit.genereteLaunchCommand(), core)
                            .setLineCallback(line -> {
                                terminal.append(line + "\n");
                                autoScrollTerminal(terminal, terminalScroll);
                            })
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                            .get();
                } catch (ExecutionException | InterruptedException ex) {
                    ex.printStackTrace();
                }
                synchronized (done) {
                    if (result) {
                        success[0]++;
                        hits.add(exploit.getTitle());
                    }
                    done[0]++;
                    int doneNow = done[0];
                    activity.runOnUiThread(() -> {
                        prog.setProgress(doneNow, true);
                        if (doneNow < total) {
                            subtitle.setText(context.getString(
                                    R.string.arsenal_run_batch_summary_running, doneNow, total));
                        }
                    });
                    if (doneNow == total) {
                        activity.runOnUiThread(() -> {
                            headerSpinner.setVisibility(View.GONE);
                            headerIcon.setVisibility(View.VISIBLE);
                            if (success[0] == 0) {
                                subtitle.setText(R.string.not_vuln_local);
                                setProgColor(prog, 1);
                                headerIcon.setColorFilter(context.getColor(R.color.red));
                            } else {
                                String hitList = android.text.TextUtils.join(", ", hits);
                                subtitle.setText(context.getString(
                                        R.string.arsenal_run_batch_vuln, hitList));
                                setProgColor(prog, 2);
                                headerIcon.setColorFilter(context.getColor(R.color.green));
                            }
                            cancel.setText(R.string.arsenal_run_batch_close);
                        });
                    }
                }
            }).start();
        }
        dialog.show();
    }

    private void autoScrollTerminal(TextView terminal, android.widget.ScrollView scroll) {
        if (terminal == null || scroll == null) return;
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    public void smoothScrool(TextView outputtext) {
        if (outputtext.getLayout() == null) return;
        final int scrollAmount = outputtext.getLayout().getLineTop(outputtext.getLineCount()) - outputtext.getHeight();
        outputtext.scrollTo(0, Math.max(scrollAmount, 0));
    }

    private void launchHydra(Device device) {
        if (core.checkFile("/data/local/stryker/release/usr/bin/hydra")) {
            core.putBoolean("hydra", true);
            com.zalexdev.stryker.hydra.HydraDialog.show(context, activity, core, device);
            return;
        }
        FragmentManager manager = ((AppCompatActivity) context).getSupportFragmentManager();
        manager.beginTransaction()
                .replace(R.id.flContent, new com.zalexdev.stryker.hydra.HydraInstall())
                .commit();
    }

    private void generatePayload() {
        com.zalexdev.stryker.metasploit.PayloadGenerator.show(context, activity, core);
    }

    @SuppressWarnings("unused")
    private void _unusedLegacyGeneratePayload() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.msfgenerate_dialog);
        final AdvancedProcess[] msfpc = {null};
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        LinearProgressIndicator prog = dialog.findViewById(R.id.progress_payload);
        AutoCompleteTextView payload = dialog.findViewById(R.id.type_payload);
        TextView cancel = dialog.findViewById(R.id.cancel_payload);
        AutoCompleteTextView port = dialog.findViewById(R.id.port_payload);
        AutoCompleteTextView ip = dialog.findViewById(R.id.ip_payload);
        AutoCompleteTextView name = dialog.findViewById(R.id.filename_payload);
        TextView generate = dialog.findViewById(R.id.launch_button);
        ShimmerFrameLayout shimmer = dialog.findViewById(R.id.msf_shim);
        payload.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, venomlist.split(" ")));
        generate.setOnClickListener(view -> {
            if (payload.getText().length() < 2 || port.getText().length() < 2
                    || ip.getText().length() < 2 || name.getText().length() < 2) {
                Toast.makeText(context, "Invalid input!", Toast.LENGTH_SHORT).show();
                return;
            }
            shimmer.showShimmer(true);
            prog.setIndeterminate(true);
            prog.setVisibility(View.VISIBLE);
            msfpc[0] = new AdvancedProcess(activity, context, "msfpc " + payload.getText().toString() + " " + ip.getText().toString() + " " + port.getText().toString(), true) {
                @Override
                public void onFinished(ArrayList<String> outputList) {
                    prog.setVisibility(View.GONE);
                    prog.setIndeterminate(false);
                    shimmer.hideShimmer();
                    Toast.makeText(context, "Payload generated!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onNewLine(String line) {
                    if (line.contains("created:")) {
                        new Thread(() -> {
                            core.createFolder("/sdcard/Stryker/payloads");
                            String path = line.replaceAll(".*:", "").replace("'[01;33m//", "").replace("[00m'", "").replaceAll("\\s+", "").trim();
                            core.customChrootCommand("cp " + path + " /sdcard/Stryker/payloads/" + path.split("/")[path.split("/").length - 1].replaceAll(".*\\.", name.getText().toString() + "."));
                        }).start();
                    }
                }

                @Override
                public void onEvent(String line) {
                }
            };
        });
        cancel.setOnClickListener(view -> {
            dialog.dismiss();
            if (msfpc[0] != null) {
                msfpc[0].kill();
            }
        });

        dialog.show();
    }

    private void testexploit(String type, String port, String ip) throws ExecutionException, InterruptedException {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.exploit_progress);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        LinearProgressIndicator prog = dialog.findViewById(R.id.exploit_prog);
        LottieAnimationView image = dialog.findViewById(R.id.exploit_img);
        TextView title = dialog.findViewById(R.id.exploit_title);
        TextView progress = dialog.findViewById(R.id.exploit_progress_text);
        TextView cancel = dialog.findViewById(R.id.exploit_cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(false);
        if (!core.is64Bit()) {
            bitdialog();
            return;
        }
        if (!type.equals("Admin")) return;

        image.setAnimation(R.raw.router);
        title.setText(R.string.rs);
        progress.setText(R.string.start_core);
        prog.setVisibility(View.INVISIBLE);
        prog.setIndeterminate(false);
        prog.setVisibility(View.VISIBLE);
        setProg(prog, 20);
        dialog.show();
        new Thread(() -> {
            try {
                Router router = new RouterScan(activity, context, progress, prog, ip, port)
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get();
                setProg(prog, 100);
                setText(cancel, context.getString(android.R.string.ok));
                if (!router.getSuccess()) {
                    setText(progress, context.getString(R.string.failed_info));
                    setProgColor(prog, image, 1);
                } else {
                    setText(progress, context.getString(R.string.webauth) + router.getAuth()
                            + context.getString(R.string.ssid) + "\n" + router.getSsid()
                            + context.getString(R.string.psk) + router.getPsk()
                            + context.getString(R.string.wps) + router.getWps());
                    setProgColor(prog, image, 2);
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void radarDialog(String ip) {
        AdvancedProcess cameradar;
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.exploit_progress);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        LinearProgressIndicator prog = dialog.findViewById(R.id.exploit_prog);
        LottieAnimationView image = dialog.findViewById(R.id.exploit_img);
        TextView title = dialog.findViewById(R.id.exploit_title);
        TextView progress = dialog.findViewById(R.id.exploit_progress_text);
        TextView cancel = dialog.findViewById(R.id.exploit_cancel);
        dialog.setCanceledOnTouchOutside(false);
        image.setAnimation(R.raw.brute);
        title.setText("Launching Cameradar");
        progress.setText("Launching...");
        prog.setVisibility(View.INVISIBLE);
        prog.setIndeterminate(false);
        prog.setVisibility(View.VISIBLE);
        setProg(prog, 20);
        dialog.show();
        final String[] username = {""};
        final String[] password = {""};
        cameradar = new AdvancedProcess(activity, context, "radar -c  /CORE/Cameradar/credentials.json -r /CORE/Cameradar/routes -t " + ip, true) {
            @Override
            public void onFinished(ArrayList<String> outputList) {
                kill();
                if (!success) {
                    setText(progress, "No credentials found");
                    setProgColor(prog, image, 1);
                } else {
                    setText(progress, "Credentials found\nUsername: " + username[0] + "\nPassword: " + password[0]);
                    setProgColor(prog, image, 2);
                }
                setProg(prog, 100);
                setText(cancel, context.getString(android.R.string.ok));
            }

            @Override
            public void onNewLine(String line) {
                progress.setText(line);
                if (line.contains("Username:")) {
                    username[0] = line.replace("Username:", "");
                }
                if (line.contains("Password:")) {
                    password[0] = line.replace("Password:", "");
                }
                onEvent(line);
            }

            @Override
            public void onEvent(String line) {
                if (line.contains("Username:") && !line.contains("not found")) success = true;
                if (line.contains("Password:") && !line.contains("not found")) success = true;
                if (line.contains("This camera does not require authentication")) success = true;
            }
        };
        AdvancedProcess finalCameradar = cameradar;
        cancel.setOnClickListener(view -> {
            dialog.dismiss();
            finalCameradar.kill();
        });
    }

    public void bitdialog() {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Your device is 32bit!")
                .setMessage(context.getString(R.string.bit))
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    public void getPort(String type, ArrayList<String> ports, String ip) {
        port = "";
        if (ports.isEmpty()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.coose_port);
        String[] port_list = ports.toArray(new String[0]);
        builder.setSingleChoiceItems(port_list, 0, (di, which) -> {
            port = port_list[which];
            di.dismiss();
        });
        AlertDialog d = builder.create();
        d.show();
        d.setOnDismissListener(dialogInterface -> {
            try {
                if (!port.isEmpty()) {
                    testexploit(type, port, ip);
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void selectPort(ArrayList<String> ports) {
        port = "";
        if (ports.isEmpty()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.coose_port);
        String[] port_list = ports.toArray(new String[0]);
        builder.setSingleChoiceItems(port_list, 0, (d, which) -> {
            portcustom = port_list[which];
            d.dismiss();
        });
        builder.create().show();
    }

    public void newDialog(Device device, int position) {
        dialog = new Dialog(activity);
        dialogip = device.getIp();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.local_main_dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        TextView ip = dialog.findViewById(R.id.device_ip);
        TextView mac = dialog.findViewById(R.id.device_mac);
        TextView os = dialog.findViewById(R.id.device_os);
        TextView portcount = dialog.findViewById(R.id.port_count);
        ImageView image = dialog.findViewById(R.id.device_image);

        View admin = dialog.findViewById(R.id.check_admin_panel);
        View vulns = dialog.findViewById(R.id.check_vulns);
        View netcut = dialog.findViewById(R.id.netcut);
        View metasploit = dialog.findViewById(R.id.launch_msf);
        View hydra = dialog.findViewById(R.id.launch_hydra);
        View wake_on_lan = dialog.findViewById(R.id.wake_on_lan);
        View genpayload = dialog.findViewById(R.id.generate_payload);
        View run_custom = dialog.findViewById(R.id.run_exploit);
        View nmap = dialog.findViewById(R.id.nmap_report);
        View camera = dialog.findViewById(R.id.camera_check);
        ShimmerFrameLayout shimmer = dialog.findViewById(R.id.shimmer);

        MaterialButton image_dialog_1 = dialog.findViewById(R.id.image_dialog_1);
        MaterialButton image_dialog_2 = dialog.findViewById(R.id.image_dialog_2);
        MaterialButton image_dialog_3 = dialog.findViewById(R.id.image_dialog_3);
        MaterialButton image_dialog_4 = dialog.findViewById(R.id.image_dialog_4);

        ArrayList<String> portStrings = device.portsToString();
        if (portStrings.contains("443")) {
            image_dialog_1.setVisibility(View.VISIBLE);
            image_dialog_1.setIconResource(R.drawable.web);
            image_dialog_1.setContentDescription(context.getString(R.string.local_action_open_https));
            image_dialog_1.setOnClickListener(v -> openlink("https://" + device.getIp()));
        }
        if (portStrings.contains("80")) {
            image_dialog_1.setVisibility(View.VISIBLE);
            image_dialog_1.setIconResource(R.drawable.web);
            image_dialog_1.setContentDescription(context.getString(R.string.local_action_open_http));
            image_dialog_1.setOnClickListener(v -> openlink("http://" + device.getIp()));
        }
        if (portStrings.contains("21") || portStrings.contains("445")) {
            image_dialog_2.setVisibility(View.VISIBLE);
            image_dialog_2.setIconResource(R.drawable.folder);
            image_dialog_2.setContentDescription(context.getString(R.string.local_action_open_ftp));
            image_dialog_2.setOnClickListener(v -> openlink("ftp://" + device.getIp()));
        }
        if (portStrings.contains("22")) {
            image_dialog_3.setVisibility(View.VISIBLE);
            image_dialog_3.setIconResource(R.drawable.terminal);
            image_dialog_3.setContentDescription(context.getString(R.string.local_action_open_ssh));
            image_dialog_3.setOnClickListener(v -> openlink("ssh://" + device.getIp()));
        }

        if (hotspot) {
            netcut.setVisibility(View.GONE);
        }

        ip.setText(device.getIp());
        if (device.getSubname() != null && !device.getSubname().isEmpty()
                && !device.getSubname().equals(device.getIp())) {
            ip.setText(device.getIp() + " (" + device.getSubname() + ")");
        }
        if (core.getBoolean("hide")) {
            mac.setText(Core.HIDDEN_MAC);
        } else {
            mac.setText(device.getMac());
        }
        os.setText(device.getOs() == null || device.getOs().isEmpty()
                ? context.getString(R.string.local_device_os_unknown)
                : device.getOs());
        portcount.setText(String.valueOf(device.getPorts().size()));
        image.setImageResource(device.getImage());

        if (device.isShim()) {
            shimmer.showShimmer(true);
        } else {
            shimmer.hideShimmer();
        }
        vulns.setOnClickListener(view -> {
            ArrayList<Exploit> vulncheck = new ArrayList<>();
            vulncheck.add(core.getExploitByTitle("EternalBlue"));
            vulncheck.add(core.getExploitByTitle("SMBGhost"));
            vulncheck.add(core.getExploitByTitle("Bluekeep"));
            vulncheck.add(core.getExploitByTitle("CVE-2022-27255"));
            for (int i = 0; i < vulncheck.size(); i++) {
                if (vulncheck.get(i) == null) continue;
                vulncheck.get(i).setIp(device.getIp());
            }
            if (vulncheck.get(3) != null) {
                vulncheck.get(3).setPort("23");
            }
            ArrayList<Exploit> filtered = new ArrayList<>();
            for (Exploit e : vulncheck) {
                if (e != null) filtered.add(e);
            }
            runexploits(filtered);
        });
        nmap.setOnClickListener(view ->
                activity.startService(new Intent(activity, NmapReportGenerator.class).putExtra("ip", device.getIp())));
        camera.setOnClickListener(view -> radarDialog(device.getIp()));
        admin.setOnClickListener(view -> getPort("Admin", device.portsToString(), device.getIp()));
        netcut.setOnClickListener(view -> netcutdialog(device, position));
        image.setOnClickListener(view -> showPorts2(device));
        genpayload.setOnClickListener(v -> generatePayload());
        metasploit.setOnClickListener(view -> {
            showMsf();
            dialog.dismiss();
        });
        hydra.setOnClickListener(view -> {
            dialog.dismiss();
            launchHydra(device);
        });
        wake_on_lan.setOnClickListener(view -> new Thread(() -> {
            wakeOnLan(device.getIp(), device.getMac());
            activity.runOnUiThread(() -> toaster(context.getString(R.string.local_wol_sent)));
        }).start());

        run_custom.setOnClickListener(view -> {
            try {
                core.updateExploits();
                String[] exploits = new String[core.getExploits().size()];
                for (int i = 0; i < core.getExploits().size(); i++) {
                    exploits[i] = core.getExploits().get(i).getTitle();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle("Select Exploit");
                builder.setItems(exploits, (dialogInterface, i) -> {
                    Exploit exploit = core.getExploits().get(i);
                    exploit.setIp(device.getIp());
                    exploit.setGw(getGateway());
                    final String[] cmd = {exploit.genereteLaunchCommand()};
                    if (exploit.getRequireArgs().size() > 0) {
                        new Thread(() -> {
                            for (String req : exploit.getRequireArgs()) {
                                activity.runOnUiThread(() -> {
                                    final Dialog valuedialog = new Dialog(context);
                                    valuedialog.setContentView(R.layout.input_dialog);
                                    Window vw = valuedialog.getWindow();
                                    if (vw != null) {
                                        vw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                        vw.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                    }
                                    TextView title = valuedialog.findViewById(R.id.title);
                                    TextInputEditText valueedit = valuedialog.findViewById(R.id.value);
                                    MaterialButton ok = valuedialog.findViewById(R.id.ok);
                                    MaterialButton dismiss = valuedialog.findViewById(R.id.cancel);
                                    dismiss.setOnClickListener(view12 -> valuedialog.dismiss());
                                    valuedialog.setCancelable(false);
                                    title.setText("[" + exploit.getTitle() + "] Enter " + req);
                                    ok.setOnClickListener(view1 -> {
                                        cmd[0] = cmd[0].replace("{" + req + "}", Objects.requireNonNull(valueedit.getText()).toString());
                                        exploit.setCommand(cmd[0]);
                                        valuedialog.dismiss();
                                    });
                                    valuedialog.show();
                                });
                            }
                            while (true) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (!cmd[0].contains("{")) {
                                    activity.runOnUiThread(() -> runexploits(new ArrayList<Exploit>() {{
                                        add(exploit);
                                    }}));
                                    break;
                                }
                            }
                        }).start();
                    } else {
                        runexploits(new ArrayList<Exploit>() {{
                            add(exploit);
                        }});
                    }
                    dialog.dismiss();
                });
                builder.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        dialog.show();
    }

    public void showPorts2(Device device) {
        final boolean[] nmap = {false};
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.local_port_viewer);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        RecyclerView recyclerView = dialog.findViewById(R.id.port_recycler);
        LottieAnimationView noports = dialog.findViewById(R.id.noports);
        View emptyState = dialog.findViewById(R.id.ports_empty_state);
        MaterialTextView portsSubtitle = dialog.findViewById(R.id.ports_subtitle);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(new PortAdapter(context, activity, device));
        MaterialButton shownmap = dialog.findViewById(R.id.show_nmap_toggle);
        TextView nmapoutput = dialog.findViewById(R.id.raw_nmap);
        View nmapCard = dialog.findViewById(R.id.raw_nmap_card);
        if (portsSubtitle != null) {
            portsSubtitle.setText(context.getString(R.string.local_ports_count, device.getPorts().size()));
        }
        if (device.getPorts().isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
        for (String o : device.getNmapoutput()) {
            nmapoutput.append(o);
            nmapoutput.append("\n");
        }
        nmapoutput.setMovementMethod(new ScrollingMovementMethod());
        shownmap.setOnClickListener(view -> {
            nmap[0] = !nmap[0];
            if (!nmap[0]) {
                recyclerView.setVisibility(View.VISIBLE);
                nmapCard.setVisibility(View.GONE);
                if (device.getPorts().isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                }
                shownmap.setText(R.string.local_ports_show_raw);
            } else {
                recyclerView.setVisibility(View.GONE);
                nmapCard.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                shownmap.setText(R.string.local_ports_show_list);
            }
        });
        dialog.show();
    }


    @Override
    public int getItemCount() {
        return devices.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setText(TextView textView, String text) {
        activity.runOnUiThread(() -> textView.setText(text));
    }

    public void setProg(LinearProgressIndicator progressIndicator, int prog) {
        activity.runOnUiThread(() -> {
            progressIndicator.setVisibility(View.INVISIBLE);
            progressIndicator.setIndeterminate(false);
            progressIndicator.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressIndicator.setProgress(prog, true);
            }
        });
    }

    public void setProgColor(LinearProgressIndicator progressIndicator, LottieAnimationView img, int color) {
        activity.runOnUiThread(() -> {
            progressIndicator.setVisibility(View.INVISIBLE);
            progressIndicator.setIndeterminate(false);
            progressIndicator.setVisibility(View.VISIBLE);
            img.setVisibility(View.VISIBLE);
            img.setRepeatCount(1);
            if (color == 1) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.red));
            } else if (color == 2) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.green));
            } else if (color == 3) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.yellow));
            }
        });
    }

    public void setProgColor(LinearProgressIndicator progressIndicator, int color) {
        activity.runOnUiThread(() -> {
            progressIndicator.setVisibility(View.INVISIBLE);
            progressIndicator.setIndeterminate(false);
            progressIndicator.setVisibility(View.VISIBLE);
            if (color == 1) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.red));
            } else if (color == 2) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.green));
            } else if (color == 3) {
                progressIndicator.setIndicatorColor(context.getColor(R.color.yellow));
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public void toaster(String msg) {
        activity.runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    public void changeitem(int i, Device d) {
        activity.runOnUiThread(() -> {
            devices.set(i, d);
            notifyItemChanged(i);
        });
    }

    public void showMsf() {
        FragmentManager manager = ((AppCompatActivity) context).getSupportFragmentManager();
        manager.beginTransaction().replace(R.id.flContent, new InstallMetasploit()).commit();
    }

    public void openlink(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(browserIntent);
        } catch (Exception e) {
            toaster(context.getString(R.string.open_link_error));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView local_ip;
        public TextView local_mac;
        public TextView local_manufacture;
        public ImageView local_img;
        public ImageView img_1;
        public ImageView img_2;
        public ImageView img_3;
        public ImageView img_4;
        public ImageView img_5;
        public TextView local_ports;
        public View ports;
        public ShimmerFrameLayout shim;
        public View card;
        public View divider;

        public ViewHolder(View v) {
            super(v);
            local_ip = v.findViewById(R.id.local_ip);
            local_mac = v.findViewById(R.id.local_mac);
            local_img = v.findViewById(R.id.local_icon);
            local_ports = v.findViewById(R.id.local_ports);
            ports = v.findViewById(R.id.port_layout);
            local_manufacture = v.findViewById(R.id.local_manufacture);
            card = v.findViewById(R.id.local_item);
            shim = v.findViewById(R.id.shimmerFrameLayout);
            img_1 = v.findViewById(R.id.img_1);
            img_2 = v.findViewById(R.id.img_2);
            img_3 = v.findViewById(R.id.img_3);
            img_4 = v.findViewById(R.id.img_4);
            img_5 = v.findViewById(R.id.img_5);
            divider = v.findViewById(R.id.local_item_divider);
        }
    }

    public String getGateway() {
        if (context == null) return "";
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return "";
        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        if (dhcp == null) return "";
        return intToIP(dhcp.gateway);
    }

    private String intToIP(int ipAddress) {
        return String.format(Locale.ENGLISH, "%d.%d.%d.%d",
                ipAddress & 0xff,
                (ipAddress >> 8) & 0xff,
                (ipAddress >> 16) & 0xff,
                (ipAddress >> 24) & 0xff);
    }

    public void setHotspot(boolean hotspot) {
        this.hotspot = hotspot;
    }

    public void wakeOnLan(String ipStr, String macStr) {
        int PORT = 9;
        DatagramSocket socket = null;
        try {
            byte[] macBytes = getMacBytes(macStr);
            byte[] bytes = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) 0xff;
            }
            for (int i = 6; i < bytes.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
            }
            InetAddress address = InetAddress.getByName(ipStr);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
            socket = new DatagramSocket();
            socket.send(packet);
        } catch (Exception ignored) {
        } finally {
            if (socket != null) socket.close();
        }
    }

    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }
}
