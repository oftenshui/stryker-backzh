package com.zalexdev.stryker.netdetect;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class AndroidUsbSource {

    private AndroidUsbSource() {}

    public static List<Entry> snapshot(Context ctx) {
        List<Entry> out = new ArrayList<>();
        UsbManager mgr = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        if (mgr == null) return out;
        HashMap<String, UsbDevice> list = mgr.getDeviceList();
        if (list == null) return out;
        for (UsbDevice dev : list.values()) out.add(Entry.from(dev));
        return out;
    }

    private static String fourHex(int n) {
        String s = Integer.toHexString(n & 0xFFFF);
        while (s.length() < 4) s = "0" + s;
        return s;
    }

    public static final class Entry {
        public final String vidPid;
        public final String deviceName;
        public final String manufacturer;
        public final String product;
        public final String serial;
        public final int interfaceCount;

        public Entry(String vidPid, String deviceName, String manufacturer,
                     String product, String serial, int interfaceCount) {
            this.vidPid = vidPid;
            this.deviceName = deviceName;
            this.manufacturer = manufacturer;
            this.product = product;
            this.serial = serial;
            this.interfaceCount = interfaceCount;
        }

        static Entry from(UsbDevice d) {
            String vidPid = fourHex(d.getVendorId()) + ":" + fourHex(d.getProductId());
            String mfr = null, prod = null, serial = null;
            try { mfr    = d.getManufacturerName(); } catch (SecurityException ignored) {}
            try { prod   = d.getProductName();      } catch (SecurityException ignored) {}
            try { serial = d.getSerialNumber();     } catch (SecurityException ignored) {}
            if ("null".equals(mfr))  mfr  = null;
            if ("null".equals(prod)) prod = null;
            return new Entry(vidPid, d.getDeviceName(), mfr, prod, serial, d.getInterfaceCount());
        }
    }
}
