package com.zalexdev.stryker.netdetect;

public final class ChipsetInfo {

    public enum Capability {
        YES,
        PARTIAL,
        CONDITIONAL,
        NO,
        NA
    }

    public enum Band { B24, B5, DUAL, NA }

    public enum Kind { WIFI, ETHERNET, MOBILE_BROADBAND, OTHER }

    public final String vendor;
    public final String chipset;
    public final String driver;
    public final Kind kind;
    public final Band band;
    public final Capability monitor;
    public final Capability injection;
    public final String firmware;
    public final String kernelMin;
    public final String notes;

    public ChipsetInfo(String vendor, String chipset, String driver, Kind kind, Band band,
                       Capability monitor, Capability injection,
                       String firmware, String kernelMin, String notes) {
        this.vendor = vendor;
        this.chipset = chipset;
        this.driver = driver;
        this.kind = kind;
        this.band = band;
        this.monitor = monitor;
        this.injection = injection;
        this.firmware = firmware;
        this.kernelMin = kernelMin;
        this.notes = notes;
    }

    public String displayName() {
        return vendor == null || vendor.isEmpty() ? chipset : vendor + " " + chipset;
    }

    public String bandLabel() {
        switch (band) {
            case B24:  return "2.4 GHz";
            case B5:   return "5 GHz";
            case DUAL: return "Dual-band";
            default:   return "—";
        }
    }
}
