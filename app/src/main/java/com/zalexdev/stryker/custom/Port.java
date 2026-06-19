package com.zalexdev.stryker.custom;

public class Port {
    private String portNumber = "0";
    private String portName = "Unknown";
    private String banner = "";


    public Port(String portNumber, String portName, String banner) {
        this.portNumber = portNumber;
        this.portName = portName;
        this.banner = banner;

    }
    public Port(){}
        public String getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(String portNumber) {
        this.portNumber = portNumber;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }



}
