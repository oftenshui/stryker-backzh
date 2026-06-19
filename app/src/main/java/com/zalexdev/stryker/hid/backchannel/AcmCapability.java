package com.zalexdev.stryker.hid.backchannel;

import android.content.Context;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.utils.Core;

import java.util.List;

public final class AcmCapability {

    public enum Verdict {
        READY,
        ROOT_DENIED,
        NODE_MISSING
    }

    public final Verdict verdict;
    public final String nodePath;
    public final boolean nodePresent;

    private AcmCapability(Verdict verdict, String nodePath, boolean nodePresent) {
        this.verdict = verdict;
        this.nodePath = nodePath;
        this.nodePresent = nodePresent;
    }

    public boolean isReady() {
        return verdict == Verdict.READY;
    }

    @NonNull
    public static AcmCapability probe(@NonNull Context context) {
        return probe(context, AcmChannel.DEFAULT_NODE);
    }

    @NonNull
    public static AcmCapability probe(@NonNull Context context, @NonNull String nodePath) {
        Core core = new Core(context);
        if (!core.checkRoot()) {
            return new AcmCapability(Verdict.ROOT_DENIED, nodePath, false);
        }
        boolean present = exists(core, nodePath);
        if (!present) {
            return new AcmCapability(Verdict.NODE_MISSING, nodePath, false);
        }
        return new AcmCapability(Verdict.READY, nodePath, true);
    }

    private static boolean exists(Core core, String path) {
        List<String> out = core.customCommand(
                "[ -e '" + path.replace("'", "'\\''") + "' ] && echo 1 || echo 0", true);
        for (String s : out) {
            if (s != null && s.contains("1")) return true;
        }
        return false;
    }
}
