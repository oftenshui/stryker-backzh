package com.zalexdev.stryker.metasploit.utils;

import android.util.Log;

import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MsfRpcConsole {

    private static final String TAG = "MsfRpcConsole";

    public enum State { IDLE, BOOTING, READY, DEAD }

    public interface Listener {
        void onLine(String line);
        void onState(State state, String reason);
    }

    private final String launchCmd = Core.EXECUTE + " ./metasploit-framework/msfconsole\n";
    private final Core core;
    private final String label;

    private Process process;
    private OutputStream stdin;
    private BufferedReader stdout;
    private Thread stderrPump;

    private volatile State state = State.IDLE;
    private volatile String version = "";
    private final Object ioLock = new Object();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private static final long CMD_TIMEOUT_MS = 60_000L;
    private static final long BOOT_TIMEOUT_MS = 180_000L;

    private Listener listener;

    public MsfRpcConsole(Core core, String label) {
        this.core = core;
        this.label = label;
    }

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    public State getState() { return state; }
    public String getVersion() { return version; }
    public boolean isReady() { return state == State.READY && isProcessAlive(); }

    public synchronized boolean boot() {
        if (state == State.READY && isProcessAlive()) return true;
        teardown("respawn");
        publishState(State.BOOTING, "starting msfconsole");
        process = core.generateSuProcess();
        if (process == null) {
            publishState(State.DEAD, "su denied");
            return false;
        }
        try {
            stdin = process.getOutputStream();
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            startStderrPump();
            stdin.write(launchCmd.getBytes());
            stdin.flush();

            long deadline = System.currentTimeMillis() + BOOT_TIMEOUT_MS;
            String line;
            while (System.currentTimeMillis() < deadline && (line = stdout.readLine()) != null) {
                line = clean(line);
                if (line.contains("metasploit v")) {
                    version = parseVersion(line);
                    publishState(State.READY, "v" + version);
                    return true;
                }
                if (line.contains("command not found") || line.contains("No such file")) {
                    publishState(State.DEAD, "msfconsole not installed");
                    return false;
                }
                if (!shouldSuppress(line)) emitLine(line);
            }
            publishState(State.DEAD, "boot timeout");
            return false;
        } catch (IOException e) {
            Log.e(TAG, label + " boot failed", e);
            publishState(State.DEAD, e.getMessage() == null ? "io error" : e.getMessage());
            return false;
        }
    }

    public ArrayList<String> command(String cmd) {
        return command(cmd, CMD_TIMEOUT_MS);
    }

    public ArrayList<String> command(String cmd, long timeoutMs) {
        ArrayList<String> out = new ArrayList<>();
        if (!isProcessAlive()) {
            if (!boot()) return out;
        }
        synchronized (ioLock) {
            String sentinel = "__STRYKER_END_" + Core.generateString().substring(0, 12) + "__";
            try {
                stdin.write((cmd + "\n").getBytes());
                stdin.write(("echo " + sentinel + "\n").getBytes());
                stdin.flush();
                long deadline = System.currentTimeMillis() + timeoutMs;
                String line;
                while (System.currentTimeMillis() < deadline && (line = stdout.readLine()) != null) {
                    if (line.contains(sentinel)) return out;
                    line = clean(line);
                    if (shouldSuppress(line)) continue;
                    out.add(line);
                    emitLine(line);
                }
                publishState(State.DEAD, "command timeout");
            } catch (Exception e) {
                Log.e(TAG, label + " io error in command", e);
                publishState(State.DEAD, e.getMessage() == null ? "io error" : e.getMessage());
            }
        }
        return out;
    }

    public void send(String cmd) {
        if (!isProcessAlive()) return;
        synchronized (ioLock) {
            try {
                stdin.write((cmd + "\n").getBytes());
                stdin.flush();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized boolean restart() {
        shuttingDown.set(false);
        teardown("manual restart");
        return boot();
    }

    public synchronized void shutdown() {
        shuttingDown.set(true);
        teardown("shutdown");
        publishState(State.IDLE, "shut down");
    }

    public boolean isProcessAlive() {
        if (process == null) return false;
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException alive) {
            return true;
        }
    }

    private void teardown(String why) {
        try { if (stdin != null) stdin.close(); } catch (IOException ignored) {}
        try { if (stdout != null) stdout.close(); } catch (IOException ignored) {}
        if (process != null) {
            try { process.destroy(); } catch (Exception ignored) {}
        }
        process = null;
        stdin = null;
        stdout = null;
        Log.d(TAG, label + " torn down (" + why + ")");
    }

    private void startStderrPump() {
        final InputStream err = process.getErrorStream();
        stderrPump = new Thread(() -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(err));
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    String cleaned = clean(line);
                    if (shouldSuppress(cleaned)) continue;
                    emitLine("[stderr] " + cleaned);
                }
            } catch (IOException ignored) {
            }
        }, "MsfRpcStderr-" + label);
        stderrPump.setDaemon(true);
        stderrPump.start();
    }

    private void publishState(State newState, String reason) {
        state = newState;
        if (listener != null) listener.onState(newState, reason);
    }

    private void emitLine(String line) {
        if (listener != null) listener.onLine(line);
    }

    private static String clean(String line) {
        if (line == null) return "";
        line = line.replaceAll("\\[[0-9;]*[a-zA-Z]", "");
        line = line.replaceAll(".*0m>", "");
        line = line.replaceFirst("^msf\\d?[^>]*>\\s?", "");
        return line.trim();
    }

    static boolean shouldSuppress(String line) {
        if (line == null || line.isEmpty()) return true;
        if (line.contains("__STRYKER_END_")) return true;
        if (line.contains("stty: standard input: Not a tty")) return true;
        if (line.startsWith("echo __STRYKER_END_")) return true;
        return false;
    }

    private static String parseVersion(String bannerLine) {
        return bannerLine.replace("metasploit v", "")
                .replace("=", "")
                .replace("[", "")
                .replace("]", "")
                .trim();
    }
}
