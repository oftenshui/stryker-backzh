package com.zalexdev.stryker.netdetect;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class UsbProbe {

    private static final String USB_DEVICES_DIR = "/sys/bus/usb/devices";
    private static final Pattern USB_DEVICE_NAME = Pattern.compile("^[0-9]+-[0-9.]+$");
    private static final FilenameFilter IFACE_FILTER =
            (dir, name) -> name.matches("[0-9]+-[0-9.]+:[0-9]+\\.[0-9]+");

    private UsbProbe() {}

    public static List<File> listUsbDevices() {
        List<File> out = new ArrayList<>();
        File root = new File(USB_DEVICES_DIR);
        File[] entries = root.listFiles();
        if (entries == null) return out;
        for (File entry : entries) {
            if (USB_DEVICE_NAME.matcher(entry.getName()).matches()
                    && new File(entry, "idVendor").exists()) {
                out.add(entry);
            }
        }
        return out;
    }

    public static File findParentUsbDevice(String netSysPath) {
        if (netSysPath == null) return null;
        File cur = new File(netSysPath);
        while (cur != null && !"/".equals(cur.getPath())) {
            if (new File(cur, "idVendor").exists() && new File(cur, "idProduct").exists()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }

    public static String readVidPid(File usbDeviceDir) {
        if (usbDeviceDir == null) return null;
        String vid = SysfsReader.readText(usbDeviceDir + "/idVendor");
        String pid = SysfsReader.readText(usbDeviceDir + "/idProduct");
        if (vid == null || pid == null) return null;
        return (vid + ":" + pid).toLowerCase();
    }

    public static List<File> listInterfaces(File usbDeviceDir) {
        List<File> out = new ArrayList<>();
        if (usbDeviceDir == null || !usbDeviceDir.isDirectory()) return out;
        File[] entries = usbDeviceDir.listFiles(IFACE_FILTER);
        if (entries == null) return out;
        for (File f : entries) out.add(f);
        return out;
    }

    public static InterfaceProbe probeInterfaces(File usbDeviceDir) {
        InterfaceProbe out = new InterfaceProbe();
        for (File iface : listInterfaces(usbDeviceDir)) {
            String driver = SysfsReader.symlinkBasename(iface + "/driver");
            String modalias = SysfsReader.readText(iface + "/modalias");
            File netDir = new File(iface, "net");
            File[] netdevs = netDir.isDirectory() ? netDir.listFiles() : null;
            if (driver != null) out.boundInterfaceCount++;
            if (netdevs != null) {
                for (File nd : netdevs) out.netdevs.add(nd.getName());
            }
            if (driver != null && !out.drivers.contains(driver)) out.drivers.add(driver);
            if (modalias != null && !out.modaliases.contains(modalias)) {
                out.modaliases.add(modalias);
            }
        }

        if (!out.netdevs.isEmpty())            out.state = DriverState.BOUND_WITH_NETDEV;
        else if (out.boundInterfaceCount > 0)  out.state = DriverState.BOUND_NO_NETDEV;
        else                                   out.state = DriverState.UNBOUND;
        return out;
    }

    public static final class InterfaceProbe {
        public final List<String> netdevs    = new ArrayList<>();
        public final List<String> drivers    = new ArrayList<>();
        public final List<String> modaliases = new ArrayList<>();
        public int boundInterfaceCount;
        public DriverState state = DriverState.UNKNOWN;
    }
}
