package com.zalexdev.stryker.utils;

import android.app.Activity;
import android.content.Context;

import com.zalexdev.stryker.logger.LogTool;
import com.zalexdev.stryker.logger.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

public abstract class SimpleProcess {

    private final Activity activity;
    private static Process process;
    private final InputStream output;
    private final InputStream error;
    private final OutputStream input;
    private final String cmd;
    private final String tool;
    private final boolean chroot;
    private final ArrayList<String> outputList = new ArrayList<>();
    private final Logger logger;
    private boolean noLog = false;
    public Core core;

    public SimpleProcess(Activity activity, String command, boolean chroot) {
        this.activity = activity;
        core = new Core((Context) activity);
        process = core.generateSuProcess();
        this.cmd = command;
        this.tool = LogTool.classify(command);
        this.chroot = chroot;
        output = process.getInputStream();
        error = process.getErrorStream();
        input = process.getOutputStream();
        logger = new Logger();
        startBackground();
    }

    private void startBackground() {
        onStarted();
        new Thread(() -> {
            sendCommand(cmd);
            logger.writeLine("Command: " + cmd, 1, tool);
            BufferedReader reader = new BufferedReader(new InputStreamReader(output));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!noLog) {
                        logger.writeLine(line, 2, tool);
                    }
                    outputList.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(error));
            try {
                while ((line = errorReader.readLine()) != null) {
                    line = line.trim();
                    if (!noLog) {
                        logger.writeLine(line, 3, tool);
                    }
                    outputList.add("[E] " + line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            activity.runOnUiThread(() -> onFinished(outputList));
        }).start();
    }

    public abstract void onFinished(ArrayList<String> outputList);

    protected void onStarted() {
    }

    public SimpleProcess sendCommand(String command) {
        try {
            if (chroot) {
                input.write((Core.EXECUTE + " '" + command + "'\nexit\n").getBytes());
            } else {
                input.write((command + "\nexit\n").getBytes());
            }
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

    public SimpleProcess setNoLog(boolean noLog) {
        this.noLog = noLog;
        return this;
    }
}
