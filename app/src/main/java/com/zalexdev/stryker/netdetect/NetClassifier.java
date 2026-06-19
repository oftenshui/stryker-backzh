package com.zalexdev.stryker.netdetect;

import java.io.File;
import java.util.regex.Pattern;

public final class NetClassifier {

    private static final Pattern USB_IN_PATH = Pattern.compile("/usb[0-9]+/");

    private static final Pattern VENDOR_BLOB_DRIVER = Pattern.compile(
            "^(bcmdhd|bcmdhd_pcie|bcmdhd_sdio|wcnss|cnss[0-9]?|qca_.*|wlan"
            + "|wlan_oem|wlan_mt[0-9a-z_]*|slsi_wlan)$");

    private NetClassifier() {}

    public static Result classify(String iface) {
        String base = "/sys/class/net/" + iface;
        if (!new File(base).exists()) {
            return new Result(BusKind.UNKNOWN, null, null);
        }

        String deviceLink = base + "/device";
        if (!new File(deviceLink).exists()) {
            return new Result(BusKind.VIRTUAL, null, null);
        }

        String sysPath   = SysfsReader.realPath(deviceLink);
        String subsystem = SysfsReader.symlinkBasename(deviceLink + "/subsystem");
        String driver    = SysfsReader.symlinkBasename(deviceLink + "/driver");

        BusKind kind = decide(subsystem, driver, sysPath);
        return new Result(kind, driver, sysPath);
    }

    private static BusKind decide(String subsystem, String driver, String sysPath) {
        if (subsystem != null) {
            switch (subsystem) {
                case "usb":      return BusKind.EXTERNAL_USB;
                case "pci":      return BusKind.INTERNAL_PCIE;
                case "sdio":
                case "mmc":      return BusKind.INTERNAL_SDIO;
                case "platform":
                    return pathContainsUsb(sysPath) ? BusKind.EXTERNAL_USB
                                                    : BusKind.INTERNAL_PLATFORM;
            }
        }
        if (pathContainsUsb(sysPath)) return BusKind.EXTERNAL_USB;
        if (driver != null && VENDOR_BLOB_DRIVER.matcher(driver).matches()) {
            return BusKind.INTERNAL_VENDOR_BLOB;
        }
        return BusKind.UNKNOWN;
    }

    private static boolean pathContainsUsb(String sysPath) {
        return sysPath != null && USB_IN_PATH.matcher(sysPath).find();
    }

    public static final class Result {
        public final BusKind kind;
        public final String driver;
        public final String sysPath;

        public Result(BusKind kind, String driver, String sysPath) {
            this.kind = kind;
            this.driver = driver;
            this.sysPath = sysPath;
        }
    }
}
