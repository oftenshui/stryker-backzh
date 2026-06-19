package com.zalexdev.stryker.netdetect;

import java.util.ArrayList;
import java.util.List;

public final class UsbDeviceReport {

    public final String vidPid;
    public final String port;
    public final String sysPath;
    public final String manufacturer;
    public final String product;
    public final String speedMbps;
    public final UsbProbe.InterfaceProbe interfaces;
    public final ChipsetInfo chipset;
    public final ChipsetSource chipsetSource;
    public final List<String> netInterfaces;
    public final List<String> warnings = new ArrayList<>();

    public enum ChipsetSource { CURATED, LEGACY, UNKNOWN }

    public UsbDeviceReport(String vidPid, String port, String sysPath,
                           String manufacturer, String product, String speedMbps,
                           UsbProbe.InterfaceProbe interfaces,
                           ChipsetInfo chipset, ChipsetSource chipsetSource) {
        this.vidPid = vidPid;
        this.port = port;
        this.sysPath = sysPath;
        this.manufacturer = manufacturer;
        this.product = product;
        this.speedMbps = speedMbps;
        this.interfaces = interfaces;
        this.chipset = chipset;
        this.chipsetSource = chipsetSource;
        this.netInterfaces = new ArrayList<>(interfaces.netdevs);
    }

    public DriverState driverState() { return interfaces.state; }

    public boolean isWifi() {
        return chipset != null && chipset.kind == ChipsetInfo.Kind.WIFI;
    }

    public String displayName() {
        if (chipset != null && chipset.chipset != null && !chipset.chipset.startsWith("Unknown")) {
            return chipset.displayName();
        }
        StringBuilder sb = new StringBuilder();
        if (manufacturer != null && !manufacturer.isEmpty()) sb.append(manufacturer);
        if (product != null && !product.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(product);
        }
        if (sb.length() == 0) sb.append("USB device ").append(vidPid);
        return sb.toString();
    }
}
