package com.zalexdev.stryker.metasploit.utils;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Argument;
import com.zalexdev.stryker.custom.MsfExploit;
import com.zalexdev.stryker.logger.Logger;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

public class MetasploitUtils {

    public Context context;
    public Core core;
    public Activity activity;
    public Logger logger = new Logger();
    public MsfUtilsHelper msfUtilsHelper;
    public String version = "";

    public final MsfRpcConsole console;
    public final MsfRpcConsole shell;

    public boolean isInitializingConsole = false;
    public boolean isInitializingShell = false;
    public boolean isInitializedConsole = false;
    public boolean isInitializedShell = false;

    public Process consoleProc;
    public Process shellProc;

    public final String launchCmd = Core.EXECUTE + " ./metasploit-framework/msfconsole\n";

    private final ArrayList<StateListener> stateListeners = new ArrayList<>();

    public interface StateListener {
        void onConsoleState(MsfRpcConsole.State state, String reason);
        void onShellState(MsfRpcConsole.State state, String reason);
    }

    public MetasploitUtils(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.core = new Core(context);
        this.console = new MsfRpcConsole(core, "console");
        this.shell = new MsfRpcConsole(core, "shell");

        console.setListener(new MsfRpcConsole.Listener() {
            @Override public void onLine(String line) {
                logger.writeLine("[msf-console] " + line, 2);
                if (msfUtilsHelper != null) msfUtilsHelper.onNewLineConsole(line);
            }
            @Override public void onState(MsfRpcConsole.State state, String reason) {
                isInitializingConsole = state == MsfRpcConsole.State.BOOTING;
                isInitializedConsole = state == MsfRpcConsole.State.READY;
                if (state == MsfRpcConsole.State.READY) version = console.getVersion();
                notificator();
                for (StateListener l : stateListeners) l.onConsoleState(state, reason);
            }
        });
        shell.setListener(new MsfRpcConsole.Listener() {
            @Override public void onLine(String line) {
                logger.writeLine("[msf-shell] " + line, 2);
                if (msfUtilsHelper != null) msfUtilsHelper.onNewLineShell(line);
            }
            @Override public void onState(MsfRpcConsole.State state, String reason) {
                isInitializingShell = state == MsfRpcConsole.State.BOOTING;
                isInitializedShell = state == MsfRpcConsole.State.READY;
                notificator();
                for (StateListener l : stateListeners) l.onShellState(state, reason);
            }
        });

        notificator();
        logger.writeLine("MetasploitUtils initialized", 2);
        new Thread(this::initConsole, "msf-console-init").start();
        new Thread(this::initShell, "msf-shell-init").start();
    }

    public void addStateListener(StateListener listener) {
        if (listener != null && !stateListeners.contains(listener)) stateListeners.add(listener);
    }

    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public boolean initConsole() {
        return console.boot();
    }

    public boolean initShell() {
        return shell.boot();
    }

    public boolean restartConsole() {
        return console.restart();
    }

    public boolean restartShell() {
        return shell.restart();
    }

    public ArrayList<String> consoleCommand(String cmd) {
        if (!console.isReady() && !console.boot()) return new ArrayList<>();
        return console.command(cmd);
    }

    public ArrayList<String> shellCommand(String cmd) {
        if (!shell.isReady()) {
            if (msfUtilsHelper != null) {
                msfUtilsHelper.onNewLineShell("Shell is not alive. Restarting, please wait!..");
            }
            shell.boot();
        }
        if (!shell.isReady()) return new ArrayList<>();
        return shell.command(cmd);
    }

    public ArrayList<MsfExploit> getExploits() {
        ArrayList<MsfExploit> exploits = new ArrayList<>();
        logger.writeLine("MetasploitUtils: Getting exploits", 2);
        ArrayList<String> out = consoleCommand("show exploits");
        for (String s : out) {
            if (s.contains("/")) {
                s = s.replaceAll("\\s+", " ").trim();
                String[] spl = s.split(" ");
                if (spl.length > 5) {
                    StringBuilder desc = new StringBuilder();
                    for (int i = 5; i < spl.length; i++) desc.append(spl[i]).append(" ");
                    MsfExploit exploit = new MsfExploit(
                            spl[1].split("/")[spl[1].split("/").length - 1],
                            desc.toString().trim(),
                            spl[2]);
                    exploit.setUrl(spl[1]);
                    exploit.setRank(spl[3]);
                    exploits.add(exploit);
                }
            }
        }
        return exploits;
    }

    public ArrayList<MsfExploit> getAuxiliary() {
        logger.writeLine("MetasploitUtils: Getting auxiliary", 2);
        ArrayList<MsfExploit> exploits = new ArrayList<>();
        ArrayList<String> out = consoleCommand("show auxiliary");
        for (String s : out) {
            s = s.trim();
            if (s.length() > 0 && s.contains("/") && Character.isDigit(s.charAt(0))) {
                s = s.replaceAll("\\s{2,}", ";").trim();
                String[] spl = s.split(";");
                if (spl.length == 6) {
                    MsfExploit exploit = new MsfExploit(
                            spl[1].split("/")[spl[1].split("/").length - 1],
                            spl[5], spl[2]);
                    exploit.setUrl(spl[1]);
                    exploit.setRank(spl[3]);
                    exploits.add(exploit);
                } else if (spl.length == 5) {
                    MsfExploit exploit = new MsfExploit(
                            spl[1].split("/")[spl[1].split("/").length - 1],
                            spl[4], "None");
                    exploit.setUrl(spl[1]);
                    exploit.setRank(spl[2]);
                    exploits.add(exploit);
                }
            }
        }
        return exploits;
    }

    public MsfExploit getArguments(MsfExploit exploit) {
        ArrayList<Argument> args = new ArrayList<>();
        consoleCommand("use " + exploit.getUrl());
        ArrayList<String> opts = consoleCommand("show options");
        ArrayList<String> out = consoleCommand("show info");
        for (String s : opts) {
            s = s.trim().replaceAll("\\s{2,}", ";");
            String[] spl = s.split(";");
            if (spl.length > 0 && Pattern.matches("[a-zA-Z]+", spl[0])
                    && spl[0].equals(spl[0].toUpperCase(Locale.ENGLISH))) {
                Argument argument = new Argument();
                if (spl.length == 4) {
                    argument.setName(spl[0].trim());
                    argument.setValue(spl[1].trim());
                    argument.setDescription(spl[3].trim());
                } else if (spl.length == 3) {
                    argument.setName(spl[0].trim());
                    argument.setDescription(spl[2].trim());
                } else if (spl.length == 2) {
                    argument.setName(spl[0].trim());
                } else {
                    continue;
                }
                argument.setRequired(s.contains(context.getResources().getString(R.string.yes)));
                args.add(argument);
            }
        }
        for (int i = 0; i < out.size(); i++) {
            String s = out.get(i);
            if (s.contains("Name:")) {
                exploit.setTitle(s.replace("Name:", "").trim());
            } else if (s.contains("Description:")) {
                StringBuilder desc = new StringBuilder();
                for (int j = i + 1; j < out.size(); j++) {
                    if (out.get(j).length() < 1) break;
                    desc.append(out.get(j)).append(" ");
                }
                exploit.setDescription(desc.toString().trim());
            } else if (s.contains("References:")) {
                for (int j = i + 1; j < out.size(); j++) {
                    if (out.get(j).contains("https://")) {
                        exploit.setReference(out.get(j));
                        break;
                    }
                }
            }
        }
        exploit.setArguments(args);
        return exploit;
    }

    public boolean isAliveConsole() {
        return console.isProcessAlive();
    }

    public boolean isAliveShell() {
        return shell.isProcessAlive();
    }

    public void killJobs() {
        new Thread(() -> {
            if (shell.isProcessAlive()) shell.send("jobs -K");
        }, "msf-killjobs").start();
    }

    public void stop() {
        new Thread(this::shutdown, "msf-stop").start();
    }

    public void shutdown() {
        console.shutdown();
        shell.shutdown();
    }

    public void notificator() {
        if (isInitializingShell || isInitializingConsole) {
            sendNotification("Metasploit", "Launching Metasploit Framework...", true);
        } else if ((isAliveConsole() && isInitializedConsole) || (isAliveShell() && isInitializedShell)) {
            sendNotification("Metasploit", "Metasploit Framework is ready", false);
        } else {
            sendNotification("Metasploit", "Failed to launch Metasploit", false);
        }
    }

    public void sendNotification(String title, String message, boolean indeterminate) {
        int notificationId = 333;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "MetasploitInitializer")
                .setSmallIcon(R.drawable.shield)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        if (indeterminate) builder.setProgress(0, 0, true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("MetasploitInitializer",
                    "MetasploitInitializer", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        nm.notify(notificationId, builder.build());
    }

    public void setMsfUtilsHelper(MsfUtilsHelper msfUtilsHelper) {
        this.msfUtilsHelper = msfUtilsHelper;
    }
}
