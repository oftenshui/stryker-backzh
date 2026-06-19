package com.zalexdev.stryker.netdetect;

import java.util.ArrayList;
import java.util.List;

public final class DetectedInterface {

    public final String name;
    public final BusKind busKind;
    public final String driver;
    public final String sysPath;
    public final String vidPid;
    public final String usbPort;
    public final ChipsetInfo chipset;
    public final String mac;
    public final String operState;
    public final String phy;
    public final List<String> warnings;

    public DetectedInterface(String name, BusKind busKind, String driver, String sysPath,
                             String vidPid, String usbPort, ChipsetInfo chipset,
                             String mac, String operState, String phy, List<String> warnings) {
        this.name = name;
        this.busKind = busKind;
        this.driver = driver;
        this.sysPath = sysPath;
        this.vidPid = vidPid;
        this.usbPort = usbPort;
        this.chipset = chipset;
        this.mac = mac;
        this.operState = operState;
        this.phy = phy;
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public boolean isExternalUsb()  { return busKind == BusKind.EXTERNAL_USB; }
    public boolean isInternalWifi() {
        return busKind == BusKind.INTERNAL_PCIE
                || busKind == BusKind.INTERNAL_SDIO
                || busKind == BusKind.INTERNAL_PLATFORM
                || busKind == BusKind.INTERNAL_VENDOR_BLOB;
    }
    public boolean isWifi() { return phy != null || (chipset != null && chipset.kind == ChipsetInfo.Kind.WIFI); }

    public String busLabel() {
        switch (busKind) {
            case EXTERNAL_USB:         return "USB-OTG";
            case INTERNAL_PCIE:        return "PCIe (internal)";
            case INTERNAL_SDIO:        return "SDIO (internal)";
            case INTERNAL_PLATFORM:    return "Platform (internal)";
            case INTERNAL_VENDOR_BLOB: return "Vendor blob (internal)";
            case VIRTUAL:              return "Virtual";
            default:                   return "Unknown bus";
        }
    }
}
