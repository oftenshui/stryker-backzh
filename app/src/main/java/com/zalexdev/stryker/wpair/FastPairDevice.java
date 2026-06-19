package com.zalexdev.stryker.wpair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FastPairDevice {

    public enum Status {
        NOT_TESTED,
        TESTING,
        VULNERABLE,
        PATCHED,
        ERROR
    }

    public enum Signal {
        EXCELLENT,
        GOOD,
        FAIR,
        WEAK,
        VERY_WEAK
    }

    private final String name;
    private final String address;
    private final boolean pairingMode;
    private final boolean hasAccountKeyFilter;
    private final String modelId;
    private final int rssi;
    private final boolean isFastPair;
    private long lastSeen;
    private Status status = Status.NOT_TESTED;

    public FastPairDevice(String name,
                          String address,
                          boolean pairingMode,
                          boolean hasAccountKeyFilter,
                          String modelId,
                          int rssi,
                          boolean isFastPair) {
        this.name = name;
        this.address = address;
        this.pairingMode = pairingMode;
        this.hasAccountKeyFilter = hasAccountKeyFilter;
        this.modelId = modelId;
        this.rssi = rssi;
        this.isFastPair = isFastPair;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public boolean isPairingMode() { return pairingMode; }
    public boolean hasAccountKeyFilter() { return hasAccountKeyFilter; }
    public String getModelId() { return modelId; }
    public int getRssi() { return rssi; }
    public boolean isFastPair() { return isFastPair; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        String known = KnownDevices.deviceName(modelId);
        if (known != null) return known;
        return isFastPair ? "Unknown Fast Pair Device" : "BLE Device";
    }

    public String manufacturer() {
        return KnownDevices.manufacturer(modelId);
    }

    public boolean isKnownVulnerable() {
        return KnownDevices.isKnownVulnerable(modelId);
    }

    public Signal signalStrength() {
        if (rssi >= -50) return Signal.EXCELLENT;
        if (rssi >= -60) return Signal.GOOD;
        if (rssi >= -70) return Signal.FAIR;
        if (rssi >= -80) return Signal.WEAK;
        return Signal.VERY_WEAK;
    }

    public static final class KnownDevices {
        public static final class Info {
            public final String name;
            public final String vendor;
            public final boolean knownVulnerable;

            Info(String name, String vendor, boolean knownVulnerable) {
                this.name = name;
                this.vendor = vendor;
                this.knownVulnerable = knownVulnerable;
            }
        }

        private static final Map<String, Info> DEVICES;
        static {
            Map<String, Info> m = new HashMap<>();
            m.put("30018E", new Info("Pixel Buds Pro 2", "Google", true));
            m.put("CD8256", new Info("WF-1000XM4", "Sony", true));
            m.put("0E30C3", new Info("WH-1000XM5", "Sony", true));
            m.put("D5BC6B", new Info("WH-1000XM6", "Sony", true));
            m.put("821F66", new Info("LinkBuds S", "Sony", true));
            m.put("F52494", new Info("Tune Buds", "JBL", true));
            m.put("718FA4", new Info("Live Pro 2", "JBL", true));
            m.put("D446A7", new Info("Tune Beam", "JBL", true));
            m.put("9D3F8A", new Info("Soundcore Liberty 4", "Anker", true));
            m.put("F0B77F", new Info("Soundcore Liberty 4 NC", "Anker", true));
            m.put("D0A72C", new Info("Ear (a)", "Nothing", true));
            m.put("D97EBA", new Info("Nord Buds 3 Pro", "OnePlus", true));
            m.put("AE3989", new Info("Redmi Buds 5 Pro", "Xiaomi", true));
            m.put("D446F9", new Info("Elite 8 Active", "Jabra", true));
            m.put("0082DA", new Info("Galaxy Buds2 Pro", "Samsung", false));
            m.put("00FA72", new Info("Galaxy Buds FE", "Samsung", false));
            m.put("F00002", new Info("QuietComfort Earbuds II", "Bose", true));
            m.put("000006", new Info("Beats Studio Buds +", "Beats", true));
            DEVICES = Collections.unmodifiableMap(m);
        }

        public static Info info(String modelId) {
            if (modelId == null) return null;
            return DEVICES.get(modelId.toUpperCase());
        }

        public static String deviceName(String modelId) {
            Info i = info(modelId);
            return i == null ? null : i.name;
        }

        public static String manufacturer(String modelId) {
            Info i = info(modelId);
            return i == null ? null : i.vendor;
        }

        public static boolean isKnownVulnerable(String modelId) {
            Info i = info(modelId);
            return i != null && i.knownVulnerable;
        }
    }
}
