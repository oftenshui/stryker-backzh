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

public abstract class AdvancedProcess {

    public static Activity activity;
    public static Context context;
    public static Process process;
    public static Core core;
    public InputStream output;
    public InputStream error;
    public OutputStream input;
    public String cmd;
    public String tool;
    public boolean chroot;
    public boolean success = false;
    public ArrayList<String> outputList = new ArrayList<>();
    public Logger logger;
    public boolean running = true;
    public boolean noLog = false;

    public AdvancedProcess(Activity activity, Context context, String command, boolean chroot) {
        AdvancedProcess.activity = activity;
        AdvancedProcess.context = context;
        core = new Core(context);
        process = core.generateSuProcess();
        this.cmd = command;
        this.tool = LogTool.classify(command);
        this.chroot = chroot;
        output = process.getInputStream();
        error = process.getErrorStream();
        input = process.getOutputStream();
        logger = new Logger();
        execute();
    }

    public AdvancedProcess(Activity activity, Context context, String command, boolean chroot, boolean inMainThread) {
        AdvancedProcess.activity = activity;
        AdvancedProcess.context = context;
        core = new Core(context);
        process = core.generateSuProcess();
        this.cmd = command;
        this.tool = LogTool.classify(command);
        this.chroot = chroot;
        output = process.getInputStream();
        error = process.getErrorStream();
        input = process.getOutputStream();
        logger = new Logger();
        if (inMainThread)
            executeInMainThread();
        else
            execute();
    }

    public AdvancedProcess setNoLog(boolean noLog) {
        this.noLog = noLog;
        return this;
    }

    private void start() {
        activity.runOnUiThread(this::onPrepare);
        sendCommand(cmd);
        logger.writeLine("Command: " + cmd, 1, tool);
        BufferedReader reader = new BufferedReader(new InputStreamReader(output));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                String finalLine = line;
                activity.runOnUiThread(() -> onNewLine(finalLine));
                if (!noLog) {
                    logger.writeLine(line, 2, tool);
                }else{
                }
                if (line.contains("JOBFINISHED")) {
                    process.destroy();
                }
                outputList.add(line);
            }
        } catch (Exception ignored) {

        }
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(error));
        try {
            while ((line = errorReader.readLine()) != null) {
                line = line.trim();
                if (!noLog) {
                    logger.writeLine(line, 3, tool);
                }else{
                }
                outputList.add("[E] " + line);
            }
        } catch (Exception ignored) {

        }
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        process.destroy();

        activity.runOnUiThread(() -> onFinished(outputList));
        running = false;
    }

    public void execute() {
        new Thread(this::start).start();
    }

    public void executeInMainThread() {
        start();
    }

    public abstract void onFinished(ArrayList<String> outputList);

    public abstract void onNewLine(String line);

    public AdvancedProcess sendCommand(String command) {
        try {
            if (chroot) {
                input.write(("/data/data/com.zalexdev.stryker/files/busybox chroot /data/local/stryker/release /usr/bin/sudo -E PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH /bin/su\n").getBytes());
                input.write((command + "\n").getBytes());
                input.write(("exit\n").getBytes());
                input.write(("exit\n").getBytes());
            } else {
                input.write((command +"\n").getBytes());
                input.write(("exit\n").getBytes());
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

    protected void onPrepare() {

    }

    public abstract void onEvent(String line);

    public boolean isSuccess() {
        return success;
    }

    public boolean isRunning() {
        return running;
    }
}
