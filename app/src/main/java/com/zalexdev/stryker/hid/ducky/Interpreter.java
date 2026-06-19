package com.zalexdev.stryker.hid.ducky;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.backchannel.AcmChannel;
import com.zalexdev.stryker.hid.keymap.KeyEntry;
import com.zalexdev.stryker.hid.keymap.Keymap;
import com.zalexdev.stryker.hid.report.HidReportStream;
import com.zalexdev.stryker.hid.report.KeyReport;
import com.zalexdev.stryker.hid.report.MouseReport;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Interpreter {

    public interface Listener {
        void onStepStart(@NonNull Step step);
        void onStepDone(@NonNull Step step);
        void onError(@NonNull Step step, @NonNull Throwable error);
        default void onCaptured(@NonNull Step step, @Nullable String text) {}
        default void onOpenViewer(@NonNull Step step) {}
    }

    private final HidReportStream keyboard;
    @Nullable private final HidReportStream mouse;
    @NonNull private final Keymap keymap;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final LinkedBlockingQueue<String> captureQueue = new LinkedBlockingQueue<>();
    @Nullable private AcmChannel captureChannel;
    @Nullable private Listener activeListener;

    private int heldModifier;
    private int heldKeycode;

    public Interpreter(@NonNull HidReportStream keyboard,
                       @Nullable HidReportStream mouse,
                       @NonNull Keymap keymap) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.keymap = keymap;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public void run(@NonNull Program program, @Nullable Listener listener)
            throws IOException, InterruptedException {
        activeListener = listener;
        boolean needsCapture = false;
        for (Step s : program.steps) {
            if (s instanceof Step.Capture) { needsCapture = true; break; }
        }
        if (needsCapture) {
            ensureCaptureChannel();
            captureQueue.clear();
        }
        try {
            for (Step step : program.steps) {
                if (cancelled.get()) return;
                if (listener != null) listener.onStepStart(step);
                try {
                    execute(step);
                    if (listener != null) listener.onStepDone(step);
                } catch (Throwable t) {
                    if (listener != null) listener.onError(step, t);
                    if (t instanceof IOException) throw (IOException) t;
                    if (t instanceof InterruptedException) throw (InterruptedException) t;
                }
            }
        } finally {
            closeCaptureChannel();
            activeListener = null;
        }
    }

    private void execute(@NonNull Step step) throws IOException, InterruptedException {
        if (step instanceof Step.Delay) {
            Thread.sleep(((Step.Delay) step).millis);
        } else if (step instanceof Step.TypeString) {
            typeString((Step.TypeString) step);
        } else if (step instanceof Step.Combo) {
            Step.Combo c = (Step.Combo) step;
            keyboard.sendKey(KeyReport.singleKey(c.modifier | heldModifier, c.keycode));
            releaseIfNotHeld();
        } else if (step instanceof Step.Hold) {
            Step.Hold h = (Step.Hold) step;
            heldModifier = h.modifier;
            heldKeycode = h.keycode;
            keyboard.sendKey(KeyReport.singleKey(heldModifier, heldKeycode));
        } else if (step instanceof Step.Release) {
            heldModifier = 0;
            heldKeycode = 0;
            keyboard.releaseAllKeys();
        } else if (step instanceof Step.MouseMove) {
            if (mouse != null) {
                Step.MouseMove mm = (Step.MouseMove) step;
                mouse.sendMouse(new MouseReport(0, mm.dx, mm.dy, 0));
            }
        } else if (step instanceof Step.MouseClick) {
            if (mouse != null) {
                Step.MouseClick mc = (Step.MouseClick) step;
                mouse.sendMouse(new MouseReport(mc.button, 0, 0, 0));
                mouse.sendMouse(new MouseReport(0, 0, 0, 0));
            }
        } else if (step instanceof Step.MouseScroll) {
            if (mouse != null) {
                Step.MouseScroll s = (Step.MouseScroll) step;
                mouse.sendMouse(new MouseReport(0, 0, 0, s.amount));
            }
        } else if (step instanceof Step.Capture) {
            doCapture((Step.Capture) step);
        } else if (step instanceof Step.OpenViewer) {
            if (activeListener != null) activeListener.onOpenViewer(step);
        }
    }

    private void doCapture(@NonNull Step.Capture step) throws InterruptedException {
        String line = null;
        if (captureChannel != null) {
            line = captureQueue.poll();
            if (line == null) {
                line = captureQueue.poll(step.timeoutMillis, TimeUnit.MILLISECONDS);
            }
        }
        if (activeListener != null) {
            activeListener.onCaptured(step, line);
        }
    }

    private void ensureCaptureChannel() {
        if (captureChannel != null) return;
        try {
            captureChannel = AcmChannel.open(new AcmChannel.Listener() {
                @Override public void onLine(@NonNull String line) {
                    captureQueue.offer(line);
                }
                @Override public void onClosed(@Nullable Throwable cause) { }
            });
        } catch (IOException e) {
            captureChannel = null;
        }
    }

    private void closeCaptureChannel() {
        if (captureChannel != null) {
            try { captureChannel.close(); } catch (Exception ignored) {}
            captureChannel = null;
        }
        captureQueue.clear();
    }

    private void typeString(@NonNull Step.TypeString step) throws IOException, InterruptedException {
        String text = step.text;
        for (int idx = 0; idx < text.length(); ) {
            if (cancelled.get()) return;
            int cp = text.codePointAt(idx);
            idx += Character.charCount(cp);
            KeyEntry entry = keymap.lookup(cp);
            if (entry == null) {
                continue;
            }
            keyboard.sendKey(KeyReport.singleKey(entry.modifier, entry.keycode));
            releaseIfNotHeld();
            if (step.perCharDelay > 0) {
                Thread.sleep(step.perCharDelay);
            }
        }
        if (step.trailingEnter) {
            keyboard.sendKey(KeyReport.singleKey(0, com.zalexdev.stryker.hid.report.UsbHid.KEY_ENTER));
            releaseIfNotHeld();
        }
    }

    private void releaseIfNotHeld() throws IOException {
        if (heldModifier == 0 && heldKeycode == 0) {
            keyboard.releaseAllKeys();
        } else {
            keyboard.sendKey(KeyReport.singleKey(heldModifier, heldKeycode));
        }
    }
}
