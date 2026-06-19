package com.zalexdev.stryker.routerscan.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.zalexdev.stryker.utils.Core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AuthLists {

    private static final String TARGET_DIR = "/sdcard/Stryker/rs";
    private static final String[] FILES = { "auth_basic.txt", "auth_digest.txt", "auth_form.txt" };
    private static final String TAG = "AuthLists";

    private AuthLists() {}

    public static int ensureDeployed(Context context) {
        if (context == null) return 0;
        Core core = new Core(context);

        core.customCommand("mkdir -p '" + TARGET_DIR + "'");
        core.customCommand("chmod 0777 '" + TARGET_DIR + "'");

        AssetManager assets = context.getAssets();
        File appPrivateDir = context.getFilesDir();
        int copied = 0;

        for (String name : FILES) {
            if (existsOnSdcard(core, name)) continue;

            File staged = new File(appPrivateDir, name);
            try (InputStream in = assets.open(name);
                 FileOutputStream fos = new FileOutputStream(staged)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                fos.flush();
            } catch (IOException e) {
                Log.w(TAG, "Could not stage " + name + ": " + e.getMessage());
                continue;
            }

            core.customCommand("cp '" + staged.getAbsolutePath() + "' '"
                    + TARGET_DIR + "/" + name + "'");
            core.customCommand("chmod 0644 '" + TARGET_DIR + "/" + name + "'");

            if (existsOnSdcard(core, name)) {
                copied++;
                Log.i(TAG, "Deployed " + name + " → " + TARGET_DIR + "/" + name);
            } else {
                Log.w(TAG, "cp via su did not land " + name + " in " + TARGET_DIR);
            }
        }
        return copied;
    }

    private static boolean existsOnSdcard(Core core, String name) {
        java.util.ArrayList<String> out = core.customCommand(
                "[ -s '" + TARGET_DIR + "/" + name + "' ] && echo present || echo missing", true);
        for (String line : out) if (line != null && line.contains("present")) return true;
        return false;
    }
}
