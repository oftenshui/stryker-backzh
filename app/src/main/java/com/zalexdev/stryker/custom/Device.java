package com.zalexdev.stryker.custom;

import com.zalexdev.stryker.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class Device {
    public String ip = "Network error...";
    public String mac = "Scanning...";
    public String vendor = "";
    public int image = 0;
    public String os = "Unknown";
    public String subname = "";
    public ArrayList<Port> ports = new ArrayList<>();
    private ArrayList<String> nmapoutput = new ArrayList<String>();
    public boolean shim = true;
    boolean iscutted = false;
    public ArrayList<String> portsFromString = new ArrayList<>();

    public Device() {
    }

    public void setPorts(ArrayList<Port> ports) {
        this.ports = ports;
    }


    public ArrayList<String> getNmapoutput() {
        return nmapoutput;
    }

    public void setNmapoutput(ArrayList<String> nmapoutput) {
        this.nmapoutput = nmapoutput;
    }
    public void addPort(Port port) {
        ports.add(port);
    }
    public void guessos(){
        ArrayList<Port> ports = getPorts();
        for (Port port : ports) {
            if (port.getPortNumber().contains("21") || port.getPortNumber().contains("22") || port.getPortNumber().contains("23")) {
                setOs("Linux");
                setImage(R.drawable.linux);
            }
            if (port.getPortNumber().contains("554") || port.getPortNumber().contains("37777")) {
                setOs("Secure Camera");
                setImage(R.drawable.nest_cam_indoor);
            }
            if (port.getPortNumber().contains("9100")) {
                setOs("Printer");
                setImage(R.drawable.printer);
            }
            if (port.getPortNumber().contains("2336") || port.getPortNumber().contains("3004") || port.getPortNumber().contains("3031")) {
                setOs("IOS/MACOS");
                setImage(R.drawable.apple);
            }
            if (port.getPortNumber().contains("3389") || port.getPortNumber().contains("135") || port.getPortNumber().contains("136") || port.getPortNumber().contains("137") || port.getPortNumber().contains("138") || port.getPortNumber().contains("139") || port.getPortNumber().contains("5357") || port.getPortNumber().contains("445") || port.getPortNumber().contains("903")) {
                setOs("Windows");
                setImage(R.drawable.windows);
            }
            if (port.getPortNumber().contains("1900")) {
                setOs("Linux");
                setImage(R.drawable.router);
            }
        }
        
    }


    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ArrayList<Port> getPorts() {
        return ports;
    }
    

    public int getImage() {

        if (image == 0) {
            image = R.drawable.devices;
        }
        if (os.contains("Windows")){
            image = R.drawable.windows;
        }
        if (os.contains("Linux")){
            image = R.drawable.linux;
        }
        if (os.contains("Android")){
            image = R.drawable.iphone;
        }
        if (os.contains("IOS")||os.contains("MacOS")||os.contains("Apple")){
            image = R.drawable.apple;
        }
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
        String ven = vendor.toLowerCase(Locale.ROOT);
        if (ven.contains("apple")) {
            setOs("MacOS/IOS");
            setImage(R.drawable.apple);
        } else if (ven.contains("microsoft")) {
            setOs("Windows");
            setImage(R.drawable.windows);
        } else if (ven.contains("hikvision") || ven.contains("dahua")) {
            setOs("Secure Camera");
            setImage(R.drawable.apple);
        }

    }
    public ArrayList<String> portsToString(){
        ArrayList<String> ports = new ArrayList<>();
        for (Port port : getPorts()){
            ports.add(port.getPortNumber());
        }
        return ports;
    }
    public String portsArrayToString(){
        StringBuilder ports = new StringBuilder();
        for (Port port : getPorts()){
            ports.append(port.getPortNumber()).append(",");
        }
        return ports.toString();
    }

    public boolean isShim() {
        return shim;
    }

    public void setShim(boolean shim) {
        this.shim = shim;
    }

    public String getSubname() {
        return subname;
    }

    public void setSubname(String subname) {
        this.subname = subname;
    }

    public boolean isIscutted() {
        return iscutted;
    }

    public void setIscutted(boolean iscutted) {
        this.iscutted = iscutted;
    }

    public String toJSON(){
        JSONObject json = new JSONObject();
        try {
            json.put("ip", getIp());
            json.put("mac", getMac());
            json.put("vendor", getVendor());
            json.put("os", getOs());
            json.put("subname", getSubname());
            json.put("iscutted", isIscutted());
            json.put("shim", isShim());
            json.put("ports", portsArrayToString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json.toString();
    }
    public void restoreFromJSON(String json){
        try {
            JSONObject jsonObject = new JSONObject(json);
            setIp(jsonObject.getString("ip"));
            setMac(jsonObject.getString("mac"));
            setVendor(jsonObject.getString("vendor"));
            setOs(jsonObject.getString("os"));
            setSubname(jsonObject.getString("subname"));
            setIscutted(jsonObject.getBoolean("iscutted"));
            setShim(jsonObject.getBoolean("shim"));
            Collections.addAll(portsFromString, jsonObject.getString("ports").split(","));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
