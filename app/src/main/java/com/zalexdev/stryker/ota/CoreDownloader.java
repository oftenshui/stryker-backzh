package com.zalexdev.stryker.ota;

import android.content.Context;

public final class CoreDownloader {

    private CoreDownloader() {
    }

    public static RemoteManifest.Asset resolve(Context context, boolean is64Bit) {
        RemoteManifest manifest = ManifestService.fetch(context);
        if (manifest != null) {
            RemoteManifest.Asset asset = is64Bit ? manifest.chroot64 : manifest.chroot32;
            if (asset != null && asset.isUsable()) {
                return asset;
            }
        }
        String fallback = is64Bit
                ? StrykerEndpoints.FALLBACK_CHROOT_64
                : StrykerEndpoints.FALLBACK_CHROOT_32;
        return new RemoteManifest.Asset(fallback, "", 0);
    }
}
