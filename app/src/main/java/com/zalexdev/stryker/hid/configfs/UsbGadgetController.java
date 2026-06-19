package com.zalexdev.stryker.hid.configfs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.capability.HidCapabilities;
import com.zalexdev.stryker.hid.capability.HidCapabilityProbe;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class UsbGadgetController {

    public static final String GADGET_NAME = "stryker";
    public static final String GADGET_BASE = "/config/usb_gadget/" + GADGET_NAME;
    private static final String VENDOR_GADGET = "/config/usb_gadget/g1";

    private static final String KEYBOARD_DESC_HEX =
            "05010906a101050719e029e71500250175019508810295017508810195057501050819012905910295017503910195067508150025650507190029658100c0";

    private static final String MOUSE_DESC_HEX =
            "05010902a1010901a10005091901290315002501950375018102950175058103050109300931093815018127950375088106c0c0";

    private final Core core;
    private final HidCapabilityProbe probe;

    public UsbGadgetController(Context context) {
        this.core = new Core(context);
        this.probe = new HidCapabilityProbe(context);
    }

    public HidCapabilities capabilities() {
        return probe.probe();
    }

    @NonNull
    public GadgetState readState() {
        boolean exists = pathExists(GADGET_BASE);
        String udc = read(GADGET_BASE + "/UDC").trim();
        boolean bound = exists && !udc.isEmpty();
        Set<GadgetFunction> linked = EnumSet.noneOf(GadgetFunction.class);
        if (exists) {
            for (GadgetFunction f : GadgetFunction.values()) {
                if (pathExists(GADGET_BASE + "/configs/c.1/" + f.functionDir)) {
                    linked.add(f);
                }
            }
        }
        String msFile = exists && linked.contains(GadgetFunction.MASS_STORAGE)
                ? read(GADGET_BASE + "/functions/mass_storage.0/lun.0/file").trim()
                : null;
        return new GadgetState(exists, bound, bound ? udc : null, linked, msFile);
    }

    public boolean prepare(@NonNull GadgetProfile profile) {
        if (!core.checkRoot()) return false;
        List<String> script = new ArrayList<>();
        script.add("set -e");
        script.add("mkdir -p " + sq(GADGET_BASE));
        script.add("mkdir -p " + sq(GADGET_BASE + "/configs/c.1"));
        script.add("mkdir -p " + sq(GADGET_BASE + "/configs/c.1/strings/0x409"));
        script.add("mkdir -p " + sq(GADGET_BASE + "/strings/0x409"));
        script.add("echo " + sq(profile.idVendor) + " > " + sq(GADGET_BASE + "/idVendor"));
        script.add("echo " + sq(profile.idProduct) + " > " + sq(GADGET_BASE + "/idProduct"));
        script.add("echo 0x0200 > " + sq(GADGET_BASE + "/bcdUSB"));
        script.add("echo " + sq(profile.manufacturer) + " > " + sq(GADGET_BASE + "/strings/0x409/manufacturer"));
        script.add("echo " + sq(profile.productName) + " > " + sq(GADGET_BASE + "/strings/0x409/product"));
        script.add("echo " + sq(profile.serialNumber) + " > " + sq(GADGET_BASE + "/strings/0x409/serialnumber"));
        script.add("echo " + sq(profile.configurationLabel) + " > "
                + sq(GADGET_BASE + "/configs/c.1/strings/0x409/configuration"));
        script.add("echo 120 > " + sq(GADGET_BASE + "/configs/c.1/MaxPower"));

        if (profile.functions.contains(GadgetFunction.HID_KEYBOARD)) {
            script.addAll(prepareHidKeyboard());
        }
        if (profile.functions.contains(GadgetFunction.HID_MOUSE)) {
            script.addAll(prepareHidMouse());
        }
        if (profile.functions.contains(GadgetFunction.MASS_STORAGE)) {
            script.addAll(prepareMassStorage(profile.massStorageImage,
                    profile.massStorageReadOnly, profile.massStorageCdrom,
                    profile.inquiryString));
        }
        if (profile.functions.contains(GadgetFunction.RNDIS)) {
            script.addAll(prepareRndis());
        }
        if (profile.functions.contains(GadgetFunction.ACM)) {
            script.addAll(prepareAcm());
        }
        if (profile.functions.contains(GadgetFunction.ECM)) {
            script.addAll(prepareEcm());
        }

        for (GadgetFunction f : profile.functions) {
            String link = GADGET_BASE + "/configs/c.1/" + f.functionDir;
            script.add("[ -e " + sq(link) + " ] || ln -s "
                    + sq(GADGET_BASE + "/functions/" + f.functionDir) + " " + sq(link));
        }

        return run(script);
    }

    @Nullable
    public String bind() {
        String udc = capabilities().udcName;
        if (udc == null) return null;
        List<String> script = new ArrayList<>();
        script.add("if [ -s " + sq(VENDOR_GADGET + "/UDC") + " ]; then "
                + "echo > " + sq(VENDOR_GADGET + "/UDC") + " || true; fi");
        script.add("echo " + sq(udc) + " > " + sq(GADGET_BASE + "/UDC"));
        script.add("sleep 1");
        if (run(script)) {
            chmodHidNodes();
            return udc;
        }
        return null;
    }

    public boolean unbind() {
        List<String> script = new ArrayList<>();
        script.add("if [ -d " + sq(GADGET_BASE) + " ]; then "
                + "echo > " + sq(GADGET_BASE + "/UDC") + " || true; fi");
        String udc = capabilities().udcName;
        if (udc != null) {
            script.add("if [ -d " + sq(VENDOR_GADGET) + " ]; then "
                    + "echo " + sq(udc) + " > " + sq(VENDOR_GADGET + "/UDC") + " || true; fi");
        }
        return run(script);
    }

    public boolean teardown() {
        List<String> script = new ArrayList<>();
        script.add("if [ -d " + sq(GADGET_BASE) + " ]; then");
        script.add("  echo > " + sq(GADGET_BASE + "/UDC") + " 2>/dev/null || true");
        script.add("  find " + sq(GADGET_BASE + "/configs/c.1") + " -maxdepth 1 -type l -delete 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE + "/configs/c.1/strings/0x409") + " 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE + "/configs/c.1") + " 2>/dev/null || true");
        script.add("  for f in " + sq(GADGET_BASE + "/functions") + "/*; do rmdir \"$f\" 2>/dev/null || true; done");
        script.add("  rmdir " + sq(GADGET_BASE + "/strings/0x409") + " 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE) + " 2>/dev/null || true");
        script.add("fi");
        return run(script);
    }

    public boolean apply(@NonNull GadgetProfile profile) {
        if (!prepare(profile)) {
            teardown();
            return false;
        }
        String udc = bind();
        if (udc == null) {
            unbind();
            return false;
        }
        return true;
    }

    public boolean swapMassStorageImage(String absoluteImagePath,
                                        boolean readOnly,
                                        boolean cdrom) {
        if (!pathExists(GADGET_BASE + "/functions/mass_storage.0/lun.0")) return false;
        List<String> script = new ArrayList<>();
        script.add("echo " + (readOnly ? "1" : "0") + " > "
                + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/ro"));
        script.add("echo " + (cdrom ? "1" : "0") + " > "
                + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/cdrom"));
        script.add("echo " + sq(absoluteImagePath == null ? "" : absoluteImagePath)
                + " > " + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/file"));
        return run(script);
    }

    public void chmodHidNodes() {
        core.customCommand("chmod 0666 /dev/hidg0 /dev/hidg1 2>/dev/null", true);
    }

    private List<String> prepareHidKeyboard() {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/hid.keyboard";
        s.add("mkdir -p " + sq(dir));
        s.add("echo 1 > " + sq(dir + "/protocol"));
        s.add("echo 1 > " + sq(dir + "/subclass"));
        s.add("echo 8 > " + sq(dir + "/report_length"));
        s.add("printf '" + hexToEscaped(KEYBOARD_DESC_HEX) + "' > " + sq(dir + "/report_desc"));
        return s;
    }

    private List<String> prepareHidMouse() {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/hid.mouse";
        s.add("mkdir -p " + sq(dir));
        s.add("echo 2 > " + sq(dir + "/protocol"));
        s.add("echo 1 > " + sq(dir + "/subclass"));
        s.add("echo 4 > " + sq(dir + "/report_length"));
        s.add("printf '" + hexToEscaped(MOUSE_DESC_HEX) + "' > " + sq(dir + "/report_desc"));
        return s;
    }

    private List<String> prepareMassStorage(@Nullable String image,
                                            boolean readOnly,
                                            boolean cdrom,
                                            @Nullable String inquiry) {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/mass_storage.0";
        s.add("mkdir -p " + sq(dir));
        s.add("echo 1 > " + sq(dir + "/stall"));
        s.add("echo " + (cdrom ? "1" : "0") + " > " + sq(dir + "/lun.0/cdrom"));
        s.add("echo " + (readOnly ? "1" : "0") + " > " + sq(dir + "/lun.0/ro"));
        s.add("echo 0 > " + sq(dir + "/lun.0/nofua"));
        s.add("echo 0 > " + sq(dir + "/lun.0/removable"));
        if (image != null && !image.isEmpty()) {
            s.add("echo " + sq(image) + " > " + sq(dir + "/lun.0/file"));
        }
        if (inquiry != null && !inquiry.isEmpty()) {
            s.add("echo " + sq(inquiry) + " > " + sq(dir + "/lun.0/inquiry_string"));
        }
        return s;
    }

    private List<String> prepareRndis() {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/rndis.usb0";
        s.add("mkdir -p " + sq(dir));
        s.add("echo RNDIS > " + sq(dir + "/os_desc/interface.rndis/compatible_id") + " 2>/dev/null || true");
        s.add("echo 5162001 > " + sq(dir + "/os_desc/interface.rndis/sub_compatible_id") + " 2>/dev/null || true");
        return s;
    }

    private List<String> prepareAcm() {
        return java.util.Collections.singletonList(
                "mkdir -p " + sq(GADGET_BASE + "/functions/acm.0"));
    }

    private List<String> prepareEcm() {
        return java.util.Collections.singletonList(
                "mkdir -p " + sq(GADGET_BASE + "/functions/ecm.usb0"));
    }

    private boolean run(List<String> commands) {
        StringBuilder sb = new StringBuilder();
        for (String c : commands) {
            sb.append(c).append('\n');
        }
        sb.append("echo __STRYKER_OK__\n");
        List<String> out = core.customCommand(sb.toString(), true);
        for (String line : out) {
            if (line != null && line.contains("__STRYKER_OK__")) return true;
        }
        return false;
    }

    private boolean pathExists(String path) {
        return contains(core.customCommand(
                "[ -e " + sq(path) + " ] && echo 1 || echo 0", true), "1");
    }

    private String read(String path) {
        StringBuilder sb = new StringBuilder();
        List<String> lines = core.customCommand("cat " + sq(path) + " 2>/dev/null", true);
        for (String line : lines) {
            if (line == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static boolean contains(List<String> list, String needle) {
        if (list == null) return false;
        for (String s : list) {
            if (s != null && s.contains(needle)) return true;
        }
        return false;
    }

    private static String sq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String hexToEscaped(String hex) {
        StringBuilder sb = new StringBuilder(hex.length() * 2);
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            sb.append("\\x").append(hex, i, i + 2);
        }
        return sb.toString();
    }
}
