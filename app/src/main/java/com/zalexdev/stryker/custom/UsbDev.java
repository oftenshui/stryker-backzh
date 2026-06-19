package com.zalexdev.stryker.custom;

public class UsbDev {
    public String ifc = "";
    public String pid = "none";
    public String commandMon = "";
    public String commandDis = "";

    public UsbDev(String ifc, String pid, String command, String commandDis) {
        this.ifc = ifc;
        this.pid = pid;
        this.commandMon = command;
        this.commandDis = commandDis;
    }



    public String getIfc() {
        return ifc;
    }

    public String getPid() {
        return pid;
    }

    public String getCommandDis() {
        return commandDis.replace("$ifc"," "+ ifc +" ");
    }

    public String getCommandMon() {
        return commandMon.replace("$ifc"," "+ ifc +" ");
    }
}
