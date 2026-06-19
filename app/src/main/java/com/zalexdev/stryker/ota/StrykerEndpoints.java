package com.zalexdev.stryker.ota;

public final class StrykerEndpoints {

    public static final String GITHUB_REPO = "https://github.com/zalexdev/strykerapp";

    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/zalexdev/strykerapp/main/stryker_manifest.json";

    public static final String FALLBACK_CHROOT_64 =
            "https://github.com/zalexdev/strykerapp/releases/download/chroot-main/chroot64.tar.gz";

    public static final String FALLBACK_CHROOT_32 =
            "https://github.com/zalexdev/strykerapp/releases/download/chroot-main/chroot32.tar.gz";

    public static final String PREFS = "stryker_ota";

    private StrykerEndpoints() {
    }
}
