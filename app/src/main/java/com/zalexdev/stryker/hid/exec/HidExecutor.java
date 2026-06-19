package com.zalexdev.stryker.hid.exec;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.zalexdev.stryker.hid.capability.HidCapabilities;
import com.zalexdev.stryker.hid.capability.HidCapabilityProbe;
import com.zalexdev.stryker.hid.configfs.UsbGadgetController;
import com.zalexdev.stryker.hid.ducky.DuckyParseException;
import com.zalexdev.stryker.hid.ducky.DuckyParser;
import com.zalexdev.stryker.hid.ducky.Interpreter;
import com.zalexdev.stryker.hid.ducky.Program;
import com.zalexdev.stryker.hid.ducky.Step;
import com.zalexdev.stryker.hid.keymap.Keymap;
import com.zalexdev.stryker.hid.keymap.KeymapRegistry;
import com.zalexdev.stryker.hid.report.HidReportStream;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public final class HidExecutor {

    public interface ProgressSink {
        @MainThread void onCapabilityChecked(@NonNull HidCapabilities caps);
        @MainThread void onStarted(int totalSteps);
        @MainThread void onStep(@NonNull Step step, int index, int total);
        @MainThread void onError(@NonNull Step step, @NonNull Throwable error);
        @MainThread void onFinished(@NonNull ExecutionResult result);
        @MainThread default void onCaptured(@NonNull Step step,
                                            @androidx.annotation.Nullable String text) {}
        @MainThread default void onOpenViewer(@NonNull Step step) {}
    }

    private final Context context;
    private final AtomicReference<Interpreter> active = new AtomicReference<>();
    private volatile Thread runner;

    public HidExecutor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRunning() {
        return runner != null && runner.isAlive();
    }

    public void cancel() {
        Interpreter i = active.get();
        if (i != null) i.cancel();
        Thread t = runner;
        if (t != null) t.interrupt();
    }

    public void run(@NonNull String source,
                    @NonNull String keymapCode,
                    @NonNull final ProgressSink sink) {
        if (isRunning()) {
            sink.onFinished(ExecutionResult.ioError("Another script is already running"));
            return;
        }
        runner = new Thread(() -> doRun(source, keymapCode, sink), "stryker-hid");
        runner.start();
    }

    @WorkerThread
    private void doRun(String source, String keymapCode, ProgressSink sink) {
        HidCapabilities caps = new HidCapabilityProbe(context).probe();
        post(() -> sink.onCapabilityChecked(caps));
        if (!caps.canInjectKeyboard()) {
            String msg;
            switch (caps.verdict) {
                case ROOT_DENIED:      msg = "Root access denied"; break;
                case KERNEL_TOO_OLD:   msg = "Kernel " + caps.kernelRelease + " is too old (need ≥ 3.19)"; break;
                case CONFIGFS_MISSING: msg = "configfs not available — kernel is not gadget-capable"; break;
                case UDC_MISSING:      msg = "No USB Device Controller found under /sys/class/udc/"; break;
                case HID_NODE_MISSING: msg = "/dev/hidg0 is missing — apply a profile in USB Arsenal first"; break;
                case SELINUX_DENIES:   msg = "SELinux denies writes to /dev/hidg0"; break;
                default:               msg = "HID not available";
            }
            post(() -> sink.onFinished(ExecutionResult.capabilityError(caps.verdict, msg)));
            return;
        }

        Keymap keymap = new KeymapRegistry(context).load(keymapCode);
        if (keymap == null) {
            post(() -> sink.onFinished(ExecutionResult.ioError("Layout '" + keymapCode + "' missing")));
            return;
        }

        Program program;
        try {
            program = new DuckyParser().parse(source);
        } catch (DuckyParseException e) {
            post(() -> sink.onFinished(ExecutionResult.parseError(e.line, e.getMessage())));
            return;
        }

        new UsbGadgetController(context).chmodHidNodes();

        HidReportStream kb = null;
        HidReportStream mouse = null;
        try {
            kb = HidReportStream.openKeyboard();
            if (caps.mouseNodePresent) {
                try {
                    mouse = HidReportStream.openMouse();
                } catch (IOException ignored) {
                }
            }
            final int total = program.steps.size();
            post(() -> sink.onStarted(total));
            Interpreter interp = new Interpreter(kb, mouse, keymap);
            active.set(interp);
            int[] counter = new int[]{0};
            interp.run(program, new Interpreter.Listener() {
                @Override public void onStepStart(@NonNull Step step) {
                    final int idx = ++counter[0];
                    post(() -> sink.onStep(step, idx, total));
                }
                @Override public void onStepDone(@NonNull Step step) { }
                @Override public void onError(@NonNull Step step, @NonNull Throwable error) {
                    post(() -> sink.onError(step, error));
                }
                @Override public void onCaptured(@NonNull Step step,
                                                 @androidx.annotation.Nullable String text) {
                    post(() -> sink.onCaptured(step, text));
                }
                @Override public void onOpenViewer(@NonNull Step step) {
                    post(() -> sink.onOpenViewer(step));
                }
            });
            post(() -> sink.onFinished(ExecutionResult.ok()));
        } catch (InterruptedException e) {
            post(() -> sink.onFinished(ExecutionResult.cancelled()));
        } catch (IOException e) {
            post(() -> sink.onFinished(ExecutionResult.ioError(e.getMessage())));
        } finally {
            if (kb != null) kb.close();
            if (mouse != null) mouse.close();
            active.set(null);
        }
    }

    private void post(@NonNull Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
