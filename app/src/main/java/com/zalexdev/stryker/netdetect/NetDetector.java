package com.zalexdev.stryker.netdetect;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NetDetector {

    private NetDetector() {}

    public static List<UsbDeviceReport> listUsbDevices(Context ctx) {
        List<AndroidUsbSource.Entry> androidEntries = AndroidUsbSource.snapshot(ctx);
        List<File> sysfsDirs = UsbProbe.listUsbDevices();
        List<UsbDeviceReport> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        for (AndroidUsbSource.Entry entry : androidEntries) {
            File usbDir = matchSysfsDir(sysfsDirs, entry);
            UsbDeviceReport report = reportFor(ctx, entry, usbDir);
            if (report != null) {
                out.add(report);
                seen.add(report.vidPid + "@" + (report.port == null ? "?" : report.port));
            }
        }

        for (File usbDir : sysfsDirs) {
            String vidPid = UsbProbe.readVidPid(usbDir);
            if (vidPid == null) continue;
            String key = vidPid + "@" + usbDir.getName();
            if (seen.contains(key)) continue;
            UsbDeviceReport report = reportFor(ctx, null, usbDir);
            if (report != null) out.add(report);
        }
        return out;
    }

    public static List<UsbDeviceReport> listNetworkUsbDevices(Context ctx) {
        List<UsbDeviceReport> all = listUsbDevices(ctx);
        List<UsbDeviceReport> known = new ArrayList<>();
        List<UsbDeviceReport> netdev = new ArrayList<>();
        List<UsbDeviceReport> rest = new ArrayList<>();

        for (UsbDeviceReport r : all) {
            if (r.chipset != null
                    && (r.chipset.kind == ChipsetInfo.Kind.WIFI
                     || r.chipset.kind == ChipsetInfo.Kind.ETHERNET
                     || r.chipset.kind == ChipsetInfo.Kind.MOBILE_BROADBAND)) {
                known.add(r);
            } else if (!r.netInterfaces.isEmpty()) {
                netdev.add(r);
            } else {
                rest.add(r);
            }
        }
        List<UsbDeviceReport> out = new ArrayList<>(all.size());
        out.addAll(known);
        out.addAll(netdev);
        out.addAll(rest);
        return out;
    }

    private static File matchSysfsDir(List<File> sysfsDirs, AndroidUsbSource.Entry entry) {
        if (entry.deviceName != null) {
            int slash = entry.deviceName.lastIndexOf('/');
            String tail = slash < 0 ? entry.deviceName : entry.deviceName.substring(slash + 1);
            for (File dir : sysfsDirs) {
                if (dir.getName().endsWith("-" + tail)) return dir;
            }
        }
        for (File dir : sysfsDirs) {
            if (entry.vidPid.equalsIgnoreCase(UsbProbe.readVidPid(dir))) return dir;
        }
        return null;
    }

    private static UsbDeviceReport reportFor(Context ctx,
                                             AndroidUsbSource.Entry androidEntry,
                                             File usbDir) {
        String vidPid = androidEntry != null ? androidEntry.vidPid
                                             : UsbProbe.readVidPid(usbDir);
        if (vidPid == null) return null;

        String[] parts = vidPid.split(":");
        String vid = parts[0], pid = parts[1];

        UsbDeviceReport.ChipsetSource source = UsbDeviceReport.ChipsetSource.UNKNOWN;
        ChipsetInfo info = ChipsetDb.lookup(vid, pid);
        if (info != null) source = UsbDeviceReport.ChipsetSource.CURATED;
        if (info == null) {
            info = LegacyDeviceDb.lookup(ctx, vid, pid);
            if (info != null) source = UsbDeviceReport.ChipsetSource.LEGACY;
        }
        if (info == null) info = ChipsetDb.unknown(vidPid);

        UsbProbe.InterfaceProbe ifaces;
        if (usbDir != null) {
            ifaces = UsbProbe.probeInterfaces(usbDir);
        } else {
            ifaces = new UsbProbe.InterfaceProbe();
            ifaces.state = DriverState.UNBOUND;
        }

        String port = usbDir != null ? usbDir.getName()
                : (androidEntry != null && androidEntry.deviceName != null
                        ? androidEntry.deviceName : null);
        String sysPath = usbDir != null ? usbDir.getAbsolutePath() : null;

        String manufacturer = pickFirst(
                androidEntry != null ? androidEntry.manufacturer : null,
                usbDir != null ? SysfsReader.readText(usbDir + "/manufacturer") : null);
        String product = pickFirst(
                androidEntry != null ? androidEntry.product : null,
                usbDir != null ? SysfsReader.readText(usbDir + "/product") : null);
        String speed = usbDir != null ? SysfsReader.readText(usbDir + "/speed") : null;

        UsbDeviceReport report = new UsbDeviceReport(
                vidPid, port, sysPath, manufacturer, product, speed,
                ifaces, info, source);
        attachWarnings(report);
        return report;
    }

    private static String pickFirst(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }

    private static void attachWarnings(UsbDeviceReport r) {
        ChipsetInfo c = r.chipset;

        boolean kernelCannotUse = r.driverState() == DriverState.UNBOUND
                || (r.driverState() == DriverState.UNKNOWN && r.netInterfaces.isEmpty());

        if (kernelCannotUse) {
            if (c != null && c.driver != null && r.chipsetSource == UsbDeviceReport.ChipsetSource.CURATED) {
                r.warnings.add("USB device is recognised, but this kernel has no driver "
                        + "bound for it. The chipset would normally use "
                        + c.driver
                        + (c.kernelMin != null ? " (mainline kernel ≥ " + c.kernelMin + ")" : "")
                        + ". Try installing the module via DKMS or boot a kernel that ships it.");
            } else if (r.chipsetSource == UsbDeviceReport.ChipsetSource.LEGACY) {
                r.warnings.add("USB device identified by VID:PID, but this phone's kernel "
                        + "cannot use it — no matching driver is loaded. You may need a custom "
                        + "kernel (NetHunter or a vendor kernel) or a DKMS module.");
            } else {
                r.warnings.add("USB device detected, but this phone with this firmware cannot "
                        + "use it — VID:PID is not in the bundled database and no driver is bound.");
            }
        }
        if (c != null) {
            if (c.firmware != null && r.driverState() == DriverState.BOUND_NO_NETDEV) {
                r.warnings.add("Driver bound but no netdev — likely missing firmware "
                        + c.firmware + " in /lib/firmware/.");
            }
            if (c.notes != null && !c.notes.isEmpty()
                    && r.chipsetSource == UsbDeviceReport.ChipsetSource.CURATED) {
                r.warnings.add(c.notes);
            }
        }
    }

    public static List<DetectedInterface> listInterfaces(Context ctx) {
        List<DetectedInterface> out = new ArrayList<>();
        File root = new File("/sys/class/net");
        File[] entries = root.listFiles();
        if (entries == null) return out;
        for (File entry : entries) {
            String name = entry.getName();
            if ("lo".equals(name)) continue;
            out.add(classifyInterface(ctx, name));
        }
        return out;
    }

    public static DetectedInterface classifyInterface(Context ctx, String name) {
        NetClassifier.Result r = NetClassifier.classify(name);

        String vidPid = null, usbPort = null;
        ChipsetInfo chipset = null;
        if (r.kind == BusKind.EXTERNAL_USB) {
            File usbDev = UsbProbe.findParentUsbDevice(r.sysPath);
            if (usbDev != null) {
                vidPid = UsbProbe.readVidPid(usbDev);
                usbPort = usbDev.getName();
                if (vidPid != null) {
                    String[] p = vidPid.split(":");
                    chipset = ChipsetDb.lookup(p[0], p[1]);
                    if (chipset == null) chipset = LegacyDeviceDb.lookup(ctx, p[0], p[1]);
                }
            }
        }

        String mac    = SysfsReader.readText("/sys/class/net/" + name + "/address");
        String oper   = SysfsReader.readText("/sys/class/net/" + name + "/operstate");
        String phy    = SysfsReader.symlinkBasename("/sys/class/net/" + name + "/phy80211");

        return new DetectedInterface(name, r.kind, r.driver, r.sysPath,
                vidPid, usbPort, chipset, mac, oper, phy, new ArrayList<>());
    }

    public static Map<BusKind, List<DetectedInterface>> groupByBus(List<DetectedInterface> all) {
        Map<BusKind, List<DetectedInterface>> grouped = new LinkedHashMap<>();
        for (BusKind k : BusKind.values()) grouped.put(k, new ArrayList<>());
        for (DetectedInterface d : all) grouped.get(d.busKind).add(d);
        Map<BusKind, List<DetectedInterface>> out = new LinkedHashMap<>();
        for (Map.Entry<BusKind, List<DetectedInterface>> e : grouped.entrySet()) {
            if (!e.getValue().isEmpty()) out.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return out;
    }
}
