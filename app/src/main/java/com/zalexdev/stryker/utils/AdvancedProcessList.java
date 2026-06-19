package com.zalexdev.stryker.utils;

import android.app.Activity;
import android.content.Context;

import com.zalexdev.stryker.logger.LogEntry;
import com.zalexdev.stryker.logger.LogStore;
import com.zalexdev.stryker.logger.LogTool;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AdvancedProcessList {

    public static Activity activity;
    public static Context context;
    public static Process process;
    public static Core core;
    public InputStream output;
    public InputStream error;
    public OutputStream input;
    public ArrayList<String> cmd;
    public String tool;
    public boolean chroot;
    public boolean success = false;
    public ArrayList<String> outputList = new ArrayList<>();

    public boolean running = true;

    public AdvancedProcessList(Activity activity, Context context, ArrayList<String> commands, boolean chroot) {
        AdvancedProcessList.activity = activity;
        AdvancedProcessList.context = context;
        core = new Core(context);
        process = core.generateSuProcess();
        this.cmd = commands;
        this.tool = classifyBatch(commands);
        this.chroot = chroot;
        output = process.getInputStream();
        error = process.getErrorStream();
        input = process.getOutputStream();
        execute();
    }

    private void startBackground() {
        final LogStore store = context != null ? LogStore.from(context) : null;
        new Thread(() -> {
            sendCommand(Core.EXECUTE + " " + "ash");
            for (String command : cmd) {
                if (store != null) store.add(LogEntry.CMD, tool, command);
                sendCommand(command);
            }
            sendCommand("exit");
            try {
                input.close();
            } catch (Exception ignored) {
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(output));
            String line;
            new Thread(() -> {
                String lineerror;
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(error));
                try {
                    while ((lineerror = errorReader.readLine()) != null) {
                        lineerror = lineerror.trim();
                        if (store != null) store.add(LogEntry.ERR, tool, lineerror);
                        outputList.add("[E] " + lineerror);
                        String finalLineerror = lineerror;
                        activity.runOnUiThread(() -> {
                            onNewLine(finalLineerror);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            try {
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    outputList.add(line);
                    Pattern p = Pattern.compile("((\\w{2}:){5}\\w{2})");
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        line = line.replace(m.group(), Core.HIDDEN_MAC);
                    }
                    String finalLine = line;
                    if (store != null) store.add(LogEntry.OUT, tool, line);

                    String finalLine1 = line;
                    activity.runOnUiThread(() -> onNewLine(finalLine1));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            process.destroy();

            activity.runOnUiThread(() -> onFinished(outputList));
            running = false;
        }).start();
    }

    public void execute() {
        startBackground();
    }

    private static String classifyBatch(ArrayList<String> commands) {
        if (commands == null) return LogTool.SHELL;
        for (String c : commands) {
            String t = LogTool.classify(c);
            if (!LogTool.SHELL.equals(t) && !LogTool.CORE.equals(t)) return t;
        }
        return LogTool.SHELL;
    }

    public abstract void onFinished(ArrayList<String> outputList);

    public abstract void onNewLine(String line);

    public AdvancedProcessList sendCommand(String command) {
        try {
            input.write((command + "\n").getBytes());
            input.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    public void kill() {
        try {
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public abstract void onEvent(String line);

    public boolean isSuccess() {
        return success;
    }

    public boolean isRunning() {
        return running;
    }
}
