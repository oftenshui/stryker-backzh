package com.zalexdev.stryker;

import android.os.Build;

import com.zalexdev.stryker.logger.LogEntry;
import com.zalexdev.stryker.logger.LogStore;
import com.zalexdev.stryker.ota.NotificationCenter;
import com.zalexdev.stryker.ota.UpdateScheduler;

public class StrykerApp extends com.stryker.terminal.App {

    @Override
    public void onCreate() {
        super.onCreate();
        LogStore store = LogStore.init(this);
        store.add(LogEntry.INFO, "session", "==== Stryker " + BuildConfig.VERSION_NAME
                + " session start ====");
        store.add(LogEntry.INFO, "session", "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE
                + (Build.SUPPORTED_64_BIT_ABIS.length > 0 ? " (64-bit)" : " (32-bit)"));
        NotificationCenter.ensureChannel(this);
        UpdateScheduler.schedule(this);
    }
}
