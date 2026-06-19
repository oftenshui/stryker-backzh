package com.zalexdev.stryker.custom;
import java.util.ArrayList;
import java.util.Comparator;

public class WiFINetwork {
    public String mac = "XX:XX:XX:XX:XX:XX";
    public String vendor = "Unknown";
    public String ssid = "Unknown";
    public Boolean wps = false;
    public Boolean is5hhz = false;
    public Boolean isOK = false;
    public Boolean isBlocked = false;
    public Boolean isVulnerable = false;
    public String model = "";
    public int power = 40;
    public int channel = 1;
    public String lon = "";
    public String lun = "";
    public String date = "";
    public String psk = "";
    public String pin = "";
    public String name = "";
    public boolean canceled = false;
    public boolean three = false;
    public ArrayList<String> info = new ArrayList<>();
    public WiFINetwork() {
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getVendor() {
        return vendor;
    }

    public String getMac() {
        return mac.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid.trim();
    }

    public Boolean getIs5hhz() {
        return channel >= 18;
    }

    public void setIs5hhz(Boolean is5hhz) {
        this.is5hhz = is5hhz;
    }

    public Boolean getWps() {
        return wps;
    }

    public void setWps(Boolean wps) {
        this.wps = wps;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public String getModel() {
        if (model != null) {
             return model.trim();
        }else{
            return null;
        }

    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public String getLun() {
        return lun;
    }

    public void setLun(String lun) {
        this.lun = lun;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getPsk() {
        return psk;
    }

    public void setPsk(String psk) {
        this.psk = psk;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Boolean getOK() {
        return isOK;
    }

    public void setOK(Boolean OK) {
        isOK = OK;
    }

    public Boolean getBlocked() {
        return isBlocked;
    }

    public void setBlocked(Boolean blocked) {
        isBlocked = blocked;
    }

    public static class WiFIComporator implements Comparator<WiFINetwork> {
        public int compare(WiFINetwork o1, WiFINetwork o2) {
            return o1.getPower() - o2.getPower();
        }
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isThree() {
        return three;
    }

    public void setThree(boolean three) {
        this.three = three;
    }

    public void setVulnerable(Boolean vulnerable) {
        isVulnerable = vulnerable;
    }
    public boolean isVulnerable() {
        return isVulnerable;
    }

    public void setInfo(ArrayList<String> info) {
        this.info = info;
    }

    public ArrayList<String> getInfo() {
        return info;
    }
}
