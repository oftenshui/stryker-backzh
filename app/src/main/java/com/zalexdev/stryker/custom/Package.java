package com.zalexdev.stryker.custom;

public class Package {

    public String name;
    public String version;
    public boolean installed;
    private boolean isPythonPackage;

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIsPythonPackage(boolean isPythonPackage) {
        this.isPythonPackage = isPythonPackage;
    }

    public boolean isPythonPackage() {
        return isPythonPackage;
    }
}
