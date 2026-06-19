package com.zalexdev.stryker.appintro.install;

public enum InstallStage {
    PREPARING("Preparing storage layout"),
    DOWNLOADING("Downloading chroot"),
    UNPACKING("Unpacking chroot"),
    MOUNTING("Mounting chroot"),
    UPGRADING("Refreshing Alpine packages"),
    DEPLOYING_EXPLOITS("Copying built-in exploits"),
    FINALIZING("Writing version marker"),
    DONE("Installation complete");

    public final String title;

    InstallStage(String title) {
        this.title = title;
    }
}
