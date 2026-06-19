package com.stryker.terminal.ui.other;

import android.app.Activity;

import com.stryker.terminal.component.config.NeoTermPath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.function.Consumer;

public class SuUtils {

    private static final String EXECUTE = NeoTermPath.ROOT_PATH + "/chroot_exec ";

    public static ArrayList<String> customCommand(String command){
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {result.add(line);}
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineError;
            while ((lineError = br2.readLine()) != null) {result.add(lineError);}
            br2.close();
        } catch (IOException e) {
        }

        process.destroy();
        return result;
    }

    public static ArrayList<String> chrootCommand(String command){
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((EXECUTE + "'ash'" + '\n').getBytes());
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {result.add(line);}
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineError;
            while ((lineError = br2.readLine()) != null) {result.add(lineError);}
            br2.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
        }
        process.destroy();
        return result;
    }

    public static Process generateSuProcess(){
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();

            try {
                process = Runtime.getRuntime().exec("echo Device is not rooted");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return  process;
    }

    public static boolean contains(ArrayList<String> list, String item){
        for (String s : list){if (s.contains(item)){return true;}}
        return false;
    }

    public static void getInterfacesList(Activity activity, Consumer<ArrayList<String>> l){
      new Thread(() -> {
        ArrayList<String> output = customCommand("ip link show | grep wlan");
        ArrayList<String> wlanList = new ArrayList<>();
        for (String s : output){
          if (s.contains("wlan")){
            wlanList.add(s.split(":")[1].trim());
          }
        }
        activity.runOnUiThread(() -> l.accept(wlanList));
      }).start();
    }

    public static void getConnectedUsbDevices(Activity activity, Consumer<ArrayList<String>> l){
      new Thread(() -> {
        ArrayList<String> output = customCommand("lsusb");
        ArrayList<String> usbList = new ArrayList<>(output);
        activity.runOnUiThread(() -> l.accept(usbList));
      }).start();
    }

    public static final class Iface {
        public final String name;
        public final String type;
        public Iface(String name, String type) {
            this.name = name;
            this.type = (type == null) ? "" : type;
        }
        public boolean isMonitor() { return type.toLowerCase().contains("monitor"); }
    }

    public static final class UsbInfo {
        public final String location;
        public final String id;
        public final String description;
        public UsbInfo(String location, String id, String description) {
            this.location = location;
            this.id = id;
            this.description = description;
        }
    }

    public static void getMonitorInterfaces(Activity activity, Consumer<ArrayList<Iface>> l){
        new Thread(() -> {
            ArrayList<String> raw = chrootCommand(
                "iw dev | grep 'Interface\\|type' | sed -r 's/type//g' | sed -r 's/Interface//g' | sed 'N;s/\\n/,/'");
            ArrayList<Iface> out = parseInterfaces(raw);
            activity.runOnUiThread(() -> l.accept(out));
        }).start();
    }

    private static ArrayList<Iface> parseInterfaces(ArrayList<String> raw){
        ArrayList<Iface> out = new ArrayList<>();
        for (String t : raw) {
            if (t == null) continue;
            String[] parts = t.trim().replaceAll("\\s+", " ").split(",");
            if (parts.length < 2) continue;
            String a = parts[0].trim();
            String b = parts[1].trim();
            String name, type;
            if (a.contains("wlan")) { name = a; type = b; }
            else if (b.contains("wlan")) { name = b; type = a; }
            else continue;
            if (!name.isEmpty()) out.add(new Iface(name, type));
        }
        return out;
    }

    public static void setMonitorMode(Activity activity, String ifc, boolean enable, Runnable done){
        new Thread(() -> {
            if (enable) {
                if (isInternal(ifc)) customCommand("svc wifi disable");
                chrootCommand(monitorCommand(ifc));
            } else {
                if (isInternal(ifc)) customCommand("svc wifi disable");
                chrootCommand(disableCommand(ifc));
                if (isInternal(ifc)
                        && !isMonitorEnabled(ifc)
                        && !isMonitorEnabled(ifc + "mon")) {
                    customCommand("ip link set " + ifc + " up");
                    customCommand("svc wifi enable");
                }
            }
            if (activity != null && done != null) activity.runOnUiThread(done);
        }).start();
    }

    private static boolean isMonitorEnabled(String ifc){
        ArrayList<String> raw = chrootCommand(
            "iw dev | grep 'Interface\\|type' | sed -r 's/type//g' | sed -r 's/Interface//g' | sed 'N;s/\\n/,/'");
        for (Iface i : parseInterfaces(raw)) {
            if (i.name.equals(ifc)) return i.isMonitor();
        }
        return false;
    }

    private static boolean isInternal(String ifc){
        return "wlan0".equals(ifc) || "swlan0".equals(ifc);
    }

    private static String monitorCommand(String ifc){
        if ("wlan0".equals(ifc))
            return "ip link set wlan0 down; echo '4' > /sys/module/wlan/parameters/con_mode; ip link set wlan0 up";
        if ("swlan0".equals(ifc))
            return "ip link set swlan0 down; echo '4' > /sys/module/wlan/parameters/con_mode; ip link set swlan0 up";
        return "airmon-ng start " + ifc;
    }

    private static String disableCommand(String ifc){
        if ("wlan0".equals(ifc))
            return "ip link set wlan0 down; echo '0' > /sys/module/wlan/parameters/con_mode; ip link set wlan0 up";
        if ("swlan0".equals(ifc))
            return "ip link set swlan0 down; echo '0' > /sys/module/wlan/parameters/con_mode";
        return "airmon-ng stop " + ifc;
    }

    public static void getUsbDevicesDetailed(Activity activity, Consumer<ArrayList<UsbInfo>> l){
        new Thread(() -> {
            ArrayList<String> raw = chrootCommand("lsusb");
            if (!hasUsbLines(raw)) raw = customCommand("lsusb");
            ArrayList<UsbInfo> out = parseUsb(raw);
            activity.runOnUiThread(() -> l.accept(out));
        }).start();
    }

    private static boolean hasUsbLines(ArrayList<String> raw){
        for (String s : raw) {
            if (s != null && s.contains("ID ")) return true;
        }
        return false;
    }

    private static ArrayList<UsbInfo> parseUsb(ArrayList<String> raw){
        ArrayList<UsbInfo> out = new ArrayList<>();
        for (String line : raw) {
            if (line == null) continue;
            int idIdx = line.indexOf("ID ");
            if (idIdx < 0) continue;
            String location = line.substring(0, idIdx).replace(":", "").trim();
            String right = line.substring(idIdx + 3).trim();
            String id, description;
            int sp = right.indexOf(' ');
            if (sp > 0) {
                id = right.substring(0, sp).trim();
                description = right.substring(sp + 1).trim();
            } else {
                id = right;
                description = "";
            }
            out.add(new UsbInfo(location, id, description));
        }
        return out;
    }

    public static  boolean checkRoot(){
        return contains(customCommand("id"),"uid=0");
    }
}
