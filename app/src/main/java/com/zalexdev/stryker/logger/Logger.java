package com.zalexdev.stryker.logger;

import android.annotation.SuppressLint;
import android.util.Log;

import com.zalexdev.stryker.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Logger {

    public final static String INPUT = "[I] ";
    public final static String OUTPUT = "[O] ";
    public final static String ERROR = "[E] ";

    private String lastTool = LogTool.SHELL;

    public Logger() {
    }

    public void writeLine(String line, int type) {
        if (line == null) return;
        String tool;
        if (type == 1) {
            tool = LogTool.classify(line);
            lastTool = tool;
            line = stripBreadcrumb(line);
        } else {
            tool = lastTool;
        }
        forward(line, type, tool);
    }

    public void writeLine(String line, int type, String tool) {
        if (line == null) return;
        if (tool == null || tool.isEmpty()) {
            writeLine(line, type);
            return;
        }
        lastTool = tool;
        if (type == 1) line = stripBreadcrumb(line);
        forward(line, type, tool);
    }

    private void forward(String line, int type, String tool) {
        LogStore store = LogStore.peek();
        if (store != null) {
            store.add(typeToLevel(type), tool, line);
        }
        Log.println(type == 3 ? Log.ERROR : Log.DEBUG, "LOGGER", line);
    }

    private static int typeToLevel(int type) {
        switch (type) {
            case 1: return LogEntry.CMD;
            case 2: return LogEntry.OUT;
            case 3: return LogEntry.ERR;
            default: return LogEntry.INFO;
        }
    }

    private static String stripBreadcrumb(String line) {
        String[] crumbs = {
                "Executing chroot command: ",
                "Executing command: ",
                "Command: ",
        };
        for (String c : crumbs) {
            if (line.startsWith(c)) return line.substring(c.length());
        }
        return line;
    }

    @SuppressLint("SdCardPath")
    public void generateNmapReport(String ip, ArrayList<String> output) {
        SimpleDateFormat sdf = new SimpleDateFormat("_dd-MM-yyyy_HH-mm-ss", Locale.US);
        @SuppressLint("SdCardPath") String path = "/data/data/com.zalexdev.stryker/files/reports/";
        File folder = new File(path);
        folder.mkdirs();
        String filename = ip + sdf.format(new Date()) + ".txt";
        File file = new File(folder, filename);
        try {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter editor = new OutputStreamWriter(fOut);
            editor.append("=====[Powered by Stryker " + BuildConfig.VERSION_NAME + "]=====" + "\n\n");
            editor.append("Nmap scan report for ").append(ip).append("\n\n");
            editor.append("==================================" + "\n\n\n");
            for (String line : output) {
                editor.append(line).append("\n");
            }
            editor.flush();
            fOut.flush();
            editor.close();
            customCommand("su -c 'cp " + path + filename + " /sdcard/Stryker/reports/'");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void customCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            process.getOutputStream().write((command + '\n').getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
        }
    }
}
