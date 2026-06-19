package com.zalexdev.stryker.hid.report;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public final class HidReportStream implements Closeable {

    public static final String KEYBOARD_NODE = "/dev/hidg0";
    public static final String MOUSE_NODE = "/dev/hidg1";

    private final Process process;
    private final OutputStream out;
    private final String nodePath;
    private boolean closed;

    private HidReportStream(Process process, OutputStream out, String nodePath) {
        this.process = process;
        this.out = out;
        this.nodePath = nodePath;
    }

    @NonNull
    public static HidReportStream openKeyboard() throws IOException {
        return open(KEYBOARD_NODE);
    }

    @NonNull
    public static HidReportStream openMouse() throws IOException {
        return open(MOUSE_NODE);
    }

    @NonNull
    public static HidReportStream open(@NonNull String nodePath) throws IOException {
        if (!ensureWritable(nodePath)) {
            throw new IOException(nodePath
                    + " is missing or not writable — apply a gadget profile that exposes it (HID-to-Go for mouse).");
        }
        Process p;
        try {
            p = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            throw new IOException("Failed to spawn su shell: " + e.getMessage(), e);
        }
        OutputStream stdin = p.getOutputStream();
        String bootstrap = "exec cat > " + shellQuote(nodePath) + "\n";
        stdin.write(bootstrap.getBytes("UTF-8"));
        stdin.flush();
        return new HidReportStream(p, stdin, nodePath);
    }

    private static boolean ensureWritable(String nodePath) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            String cmd = "chmod 0666 " + shellQuote(nodePath) + " 2>/dev/null; "
                       + "test -w " + shellQuote(nodePath) + " && exit 0 || exit 1\n";
            p.getOutputStream().write(cmd.getBytes("UTF-8"));
            p.getOutputStream().flush();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public void send(@NonNull byte[] frame) throws IOException {
        if (closed) throw new IOException("HidReportStream is closed");
        out.write(frame);
        out.flush();
    }

    public void sendKey(@NonNull KeyReport report) throws IOException {
        send(report.toBytes());
    }

    public void releaseAllKeys() throws IOException {
        send(KeyReport.EMPTY.toBytes());
    }

    public void sendMouse(@NonNull MouseReport report) throws IOException {
        send(report.toBytes());
    }

    public String getNodePath() {
        return nodePath;
    }

    @Override
    public void close() {
        closed = true;
        try { out.close(); } catch (Exception ignored) {}
        try { process.destroy(); } catch (Exception ignored) {}
    }
}
