package com.zalexdev.stryker.netdetect;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.zalexdev.stryker.netdetect.ChipsetInfo.Band.B24;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Band.DUAL;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Band.NA;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Capability.CONDITIONAL;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Capability.NO;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Capability.PARTIAL;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Capability.YES;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Kind.ETHERNET;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Kind.MOBILE_BROADBAND;
import static com.zalexdev.stryker.netdetect.ChipsetInfo.Kind.WIFI;

public final class ChipsetDb {

    private static final Map<String, ChipsetInfo> TABLE = build();

    private ChipsetDb() {}

    public static ChipsetInfo lookup(String vid, String pid) {
        if (vid == null || pid == null) return null;
        return TABLE.get((vid + ":" + pid).toLowerCase());
    }

    public static ChipsetInfo unknown(String vidPid) {
        return new ChipsetInfo(null, "Unknown device (" + vidPid + ")", null,
                ChipsetInfo.Kind.OTHER, NA, ChipsetInfo.Capability.NA,
                ChipsetInfo.Capability.NA, null, null,
                "No entry in the bundled chipset database.");
    }

    private static Map<String, ChipsetInfo> build() {
        Map<String, ChipsetInfo> m = new HashMap<>();

        ChipsetInfo ar9271 = new ChipsetInfo("Atheros", "AR9271", "ath9k_htc",
                WIFI, B24, YES, YES, "htc_9271.fw", "2.6.35",
                "Reference dongle. Full monitor + injection via mac80211.");
        m.put("0cf3:9271", ar9271);
        m.put("0cf3:1006", ar9271);
        m.put("13d3:3327", ar9271);
        m.put("0846:9030", ar9271);

        ChipsetInfo ar7010 = new ChipsetInfo("Atheros", "AR7010", "ath9k_htc",
                WIFI, DUAL, YES, YES, "htc_7010.fw", "2.6.35",
                "Dual-band ath9k_htc variant.");
        m.put("0cf3:7010", ar7010);
        m.put("0cf3:7015", ar7010);
        m.put("0cf3:b002", ar7010);
        m.put("0cf3:b003", ar7010);

        ChipsetInfo ar9170 = new ChipsetInfo("Atheros", "AR9170", "carl9170",
                WIFI, DUAL, YES, YES, "carl9170-1.fw", "2.6.37",
                "Older carl9170. Monitor + injection.");
        m.put("0cf3:1002", ar9170);
        m.put("0cf3:9170", ar9170);
        m.put("0846:9001", ar9170);

        ChipsetInfo rtl8188cus = new ChipsetInfo("Realtek", "RTL8188CUS", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4",
                "mac80211. Injection works but less reliable than Atheros.");
        m.put("0bda:8176", rtl8188cus);
        m.put("0bda:8177", rtl8188cus);
        m.put("7392:7811", new ChipsetInfo("Edimax", "EW-7811Un (RTL8188CUS)", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4", null));

        ChipsetInfo rtl8192cu = new ChipsetInfo("Realtek", "RTL8192CU", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4", null);
        m.put("0bda:8178", rtl8192cu);
        m.put("0bda:817b", rtl8192cu);

        ChipsetInfo rtl8192eu = new ChipsetInfo("Realtek", "RTL8192EU", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4", null);
        m.put("0bda:818b", rtl8192eu);

        ChipsetInfo rtl8723bu = new ChipsetInfo("Realtek", "RTL8723BU", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4", null);
        m.put("0bda:b720", rtl8723bu);

        ChipsetInfo rtl8188fu = new ChipsetInfo("Realtek", "RTL8188FU", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "4.4", null);
        m.put("0bda:f179", rtl8188fu);

        ChipsetInfo rtl8188eus = new ChipsetInfo("Realtek", "RTL8188EUS", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "6.3",
                "Pre-6.3 kernels need the r8188eu staging driver (5.16–6.2) or "
                + "aircrack-ng/rtl8188eus DKMS.");
        m.put("0bda:8179", rtl8188eus);
        m.put("2357:010c", new ChipsetInfo("TP-Link", "TL-WN722N v2/v3 (RTL8188EUS)", "rtl8xxxu",
                WIFI, B24, YES, PARTIAL, null, "6.3",
                "Do NOT confuse with the v1 (AR9271). Same VID:PID family as RTL8188EUS."));

        ChipsetInfo rtl8821cu = new ChipsetInfo("Realtek", "RTL8821CU", "rtw88_8821cu",
                WIFI, DUAL, YES, YES, null, "6.2",
                "rtw88_usb. Monitor + injection. No active-monitor / VIF.");
        m.put("0bda:c811", rtl8821cu);
        m.put("0bda:c820", rtl8821cu);

        ChipsetInfo rtl8822bu = new ChipsetInfo("Realtek", "RTL8822BU", "rtw88_8822bu",
                WIFI, DUAL, YES, YES, null, "6.2", null);
        m.put("0bda:b82c", rtl8822bu);
        m.put("0bda:2102", rtl8822bu);

        ChipsetInfo rtl8822cu = new ChipsetInfo("Realtek", "RTL8822CU", "rtw88_8822cu",
                WIFI, DUAL, YES, YES, null, "6.2", null);
        m.put("0bda:c82f", rtl8822cu);

        ChipsetInfo rtl8812au = new ChipsetInfo("Realtek", "RTL8812AU", "rtw88_8812au",
                WIFI, DUAL, YES, YES, null, "6.14",
                "Mainline since 6.14. On older kernels needs aircrack-ng/rtl8812au DKMS.");
        m.put("0bda:8812", rtl8812au);
        m.put("2357:011e", rtl8812au);
        m.put("2357:0120", rtl8812au);
        m.put("7392:a811", rtl8812au);
        m.put("0e66:0023", rtl8812au);

        ChipsetInfo rt5370 = new ChipsetInfo("Ralink", "RT5370", "rt2800usb",
                WIFI, B24, YES, YES, null, "2.6.31", null);
        m.put("148f:5370", rt5370);
        m.put("148f:5372", rt5370);

        ChipsetInfo rt3070 = new ChipsetInfo("Ralink", "RT3070", "rt2800usb",
                WIFI, B24, YES, YES, null, "2.6.31", null);
        m.put("148f:3070", rt3070);
        m.put("148f:3071", rt3070);
        m.put("148f:3072", rt3070);

        ChipsetInfo rt3572 = new ChipsetInfo("Ralink", "RT3572", "rt2800usb",
                WIFI, DUAL, YES, YES, null, "2.6.31",
                "Dual-band Alfa AWUS036NH variants.");
        m.put("148f:3572", rt3572);
        m.put("148f:3573", rt3572);

        ChipsetInfo rt5572 = new ChipsetInfo("Ralink", "RT5572", "rt2800usb",
                WIFI, DUAL, YES, YES, null, "2.6.31", null);
        m.put("148f:5572", rt5572);

        ChipsetInfo mt7601u = new ChipsetInfo("MediaTek", "MT7601U", "mt7601u",
                WIFI, B24, PARTIAL, NO, "mt7601u.bin", "4.2",
                "Cheap clones often ship unprogrammed EEPROM and never work. Not VIF-capable.");
        m.put("148f:7601", mt7601u);
        m.put("0e8d:760a", mt7601u);
        m.put("0e8d:760b", mt7601u);

        ChipsetInfo mt7610u = new ChipsetInfo("MediaTek", "MT7610U", "mt76x0u",
                WIFI, DUAL, YES, YES, "mediatek/mt7610u.bin", "4.19", null);
        m.put("0e8d:7610", mt7610u);
        m.put("13b1:003e", mt7610u);

        ChipsetInfo mt7612u = new ChipsetInfo("MediaTek", "MT7612U", "mt76x2u",
                WIFI, DUAL, YES, YES, "mediatek/mt7662u.bin", "4.19",
                "Excellent monitor + injection, VIF-capable. Recommended for pentesting.");
        m.put("0e8d:7612", mt7612u);
        m.put("0b05:17eb", mt7612u);
        m.put("045e:02e6", mt7612u);
        m.put("0846:9053", mt7612u);

        ChipsetInfo mt7921au = new ChipsetInfo("MediaTek", "MT7921AU", "mt7921u",
                WIFI, DUAL, CONDITIONAL, YES, "mediatek/WIFI_MT7961_patch_mcu_1a_2_hdr.bin",
                "5.18",
                "Wi-Fi 6. Monitor mode has been reported broken on some kernel "
                + "versions (notably 6.18 regression, 2024 active-monitor reports). "
                + "Verify with `aireplay-ng --test`.");
        m.put("0e8d:7961", mt7921au);
        m.put("0846:9060", mt7921au);
        m.put("0846:9065", mt7921au);

        ChipsetInfo rtl8152 = new ChipsetInfo("Realtek", "RTL8152 (100M)", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null);
        m.put("0bda:8152", rtl8152);

        ChipsetInfo rtl8153 = new ChipsetInfo("Realtek", "RTL8153 (1G)", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null);
        m.put("0bda:8153", rtl8153);
        m.put("2357:0601", new ChipsetInfo("TP-Link", "UE300 (RTL8153)", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null));
        m.put("045e:07ab", new ChipsetInfo("Microsoft", "Surface Ethernet (RTL8153)", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null));
        m.put("045e:07c6", new ChipsetInfo("Microsoft", "Surface Dock Ethernet", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, "6.3", null));

        ChipsetInfo rtl8156 = new ChipsetInfo("Realtek", "RTL8156B (2.5G)", "r8152",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null);
        m.put("0bda:8156", rtl8156);

        ChipsetInfo ax88179 = new ChipsetInfo("ASIX", "AX88179 (1G)", "ax88179_178a",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null);
        m.put("0b95:1790", ax88179);
        m.put("0b95:178a", ax88179);
        m.put("0b95:17a0", new ChipsetInfo("ASIX", "AX88179A (1G)", "ax88179_178a",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, "6.0", null));
        m.put("2001:4a00", new ChipsetInfo("D-Link", "DUB-1312 (AX88179)", "ax88179_178a",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null));

        ChipsetInfo ax88772 = new ChipsetInfo("ASIX", "AX88772 (100M)", "asix",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null);
        m.put("0b95:7720", ax88772);
        m.put("0b95:772a", ax88772);
        m.put("0b95:772b", ax88772);
        m.put("2001:1a00", new ChipsetInfo("D-Link", "DUB-E100 (AX88772)", "asix",
                ETHERNET, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, null));

        ChipsetInfo tetheredRndis = new ChipsetInfo("Generic", "RNDIS tether", "rndis_host",
                MOBILE_BROADBAND, NA, ChipsetInfo.Capability.NA, ChipsetInfo.Capability.NA,
                null, null, "Class-based USB tether; matches by interface class, not VID:PID.");

        return Collections.unmodifiableMap(m);
    }
}
