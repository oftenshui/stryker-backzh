package com.zalexdev.stryker.hid.capability;

import android.content.Context;

import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class HidCapabilityProbe {

    private static final int MIN_KERNEL_MAJOR = 3;
    private static final int MIN_KERNEL_MINOR = 19;

    private final Core core;

    public HidCapabilityProbe(Context context) {
        this.core = new Core(context);
    }

    public HidCapabilities probe() {
        boolean rooted = core.checkRoot();
        if (!rooted) {
            return new HidCapabilities(false, "", false, false, null,
                    false, false, false, false, false,
                    HidCapabilities.Verdict.ROOT_DENIED);
        }

        String kernel = firstNonEmpty(core.customCommand("uname -r", true));
        boolean kernelOk = isKernelAtLeast(kernel, MIN_KERNEL_MAJOR, MIN_KERNEL_MINOR);
        if (!kernelOk) {
            return new HidCapabilities(true, kernel, false, false, null,
                    false, false, false, false, isSelinuxEnforcing(),
                    HidCapabilities.Verdict.KERNEL_TOO_OLD);
        }

        boolean configFsAvailable = pathExists("/sys/kernel/config")
                || pathExists("/config/usb_gadget");
        boolean configFsMounted = pathExists("/config/usb_gadget");
        if (!configFsAvailable) {
            return new HidCapabilities(true, kernel, false, false, null,
                    false, false, false, false, isSelinuxEnforcing(),
                    HidCapabilities.Verdict.CONFIGFS_MISSING);
        }

        String udc = discoverUdc();
        if (udc == null) {
            return new HidCapabilities(true, kernel, true, configFsMounted, null,
                    false, false, false, false, isSelinuxEnforcing(),
                    HidCapabilities.Verdict.UDC_MISSING);
        }

        boolean hidg0 = pathExists("/dev/hidg0");
        boolean hidg1 = pathExists("/dev/hidg1");
        boolean mass = pathExists("/sys/class/scsi_host") || pathExists("/sys/module/g_mass_storage");
        boolean rndis = pathExists("/sys/module/usb_f_rndis") || pathExists("/sys/module/usb_f_gsi");
        boolean selinux = isSelinuxEnforcing();

        HidCapabilities.Verdict verdict;
        if (!hidg0) {
            verdict = HidCapabilities.Verdict.HID_NODE_MISSING;
        } else {
            verdict = HidCapabilities.Verdict.READY;
        }

        return new HidCapabilities(true, kernel, true, configFsMounted, udc,
                hidg0, hidg1, mass, rndis, selinux, verdict);
    }

    private boolean pathExists(String path) {
        List<String> out = core.customCommand(
                "[ -e " + shellQuote(path) + " ] && echo 1 || echo 0", true);
        return contains(out, "1");
    }

    private String discoverUdc() {
        List<String> entries = core.customCommand("ls -1 /sys/class/udc 2>/dev/null", true);
        for (String s : entries) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty() && !t.startsWith("ls:")) {
                return t;
            }
        }
        return null;
    }

    private boolean isSelinuxEnforcing() {
        return contains(core.customCommand("getenforce 2>/dev/null", true), "Enforcing");
    }

    static boolean isKernelAtLeast(String release, int wantMajor, int wantMinor) {
        if (release == null) return false;
        String[] head = release.split("[^0-9]");
        List<Integer> nums = new ArrayList<>(2);
        for (String s : head) {
            if (s.isEmpty()) continue;
            try {
                nums.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
            if (nums.size() >= 2) break;
        }
        if (nums.size() < 2) return false;
        int major = nums.get(0);
        int minor = nums.get(1);
        if (major != wantMajor) return major > wantMajor;
        return minor >= wantMinor;
    }

    private static String firstNonEmpty(List<String> list) {
        if (list == null) return "";
        for (String s : list) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    private static boolean contains(List<String> list, String needle) {
        if (list == null) return false;
        for (String s : list) {
            if (s != null && s.contains(needle)) return true;
        }
        return false;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public static HidCapabilities cached(Context context) {
        return new HidCapabilityProbe(context).probe();
    }

    public static boolean nodeWritable(String path) {
        try {
            File f = new File(path);
            return f.exists() && f.canWrite();
        } catch (SecurityException e) {
            return false;
        }
    }
}
