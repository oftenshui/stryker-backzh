package com.zalexdev.stryker.netdetect;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

final class SysfsReader {

    private static final String TAG = "SysfsReader";

    private SysfsReader() {}

    static String readText(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return null;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            return line == null ? null : line.trim();
        } catch (IOException e) {
            Log.v(TAG, "readText failed: " + path + " — " + e.getMessage());
            return null;
        }
    }

    static String realPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
    }

    static String symlinkBasename(String symlinkPath) {
        String resolved = realPath(symlinkPath);
        if (resolved == null) return null;
        int slash = resolved.lastIndexOf('/');
        return slash < 0 ? resolved : resolved.substring(slash + 1);
    }
}
