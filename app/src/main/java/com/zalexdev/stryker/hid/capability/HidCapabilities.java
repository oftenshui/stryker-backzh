package com.zalexdev.stryker.hid.capability;

import androidx.annotation.NonNull;

public final class HidCapabilities {

    public enum Verdict {
        READY,
        ROOT_DENIED,
        KERNEL_TOO_OLD,
        CONFIGFS_MISSING,
        UDC_MISSING,
        HID_NODE_MISSING,
        SELINUX_DENIES
    }

    public final boolean rooted;
    public final String kernelRelease;
    public final boolean kernelSupportsConfigFs;
    public final boolean configFsMounted;
    public final String udcName;
    public final boolean keyboardNodePresent;
    public final boolean mouseNodePresent;
    public final boolean massStorageSupported;
    public final boolean rndisSupported;
    public final boolean selinuxEnforcing;
    public final Verdict verdict;

    HidCapabilities(boolean rooted,
                    String kernelRelease,
                    boolean kernelSupportsConfigFs,
                    boolean configFsMounted,
                    String udcName,
                    boolean keyboardNodePresent,
                    boolean mouseNodePresent,
                    boolean massStorageSupported,
                    boolean rndisSupported,
                    boolean selinuxEnforcing,
                    Verdict verdict) {
        this.rooted = rooted;
        this.kernelRelease = kernelRelease;
        this.kernelSupportsConfigFs = kernelSupportsConfigFs;
        this.configFsMounted = configFsMounted;
        this.udcName = udcName;
        this.keyboardNodePresent = keyboardNodePresent;
        this.mouseNodePresent = mouseNodePresent;
        this.massStorageSupported = massStorageSupported;
        this.rndisSupported = rndisSupported;
        this.selinuxEnforcing = selinuxEnforcing;
        this.verdict = verdict;
    }

    public boolean canInjectKeyboard() {
        return verdict == Verdict.READY && keyboardNodePresent;
    }

    public boolean canManageGadget() {
        return rooted && configFsMounted && udcName != null;
    }

    @NonNull
    @Override
    public String toString() {
        return "HidCapabilities{verdict=" + verdict
                + ", kernel=" + kernelRelease
                + ", configfs=" + configFsMounted
                + ", udc=" + udcName
                + ", hidg0=" + keyboardNodePresent
                + ", hidg1=" + mouseNodePresent
                + ", selinux=" + selinuxEnforcing
                + '}';
    }
}
