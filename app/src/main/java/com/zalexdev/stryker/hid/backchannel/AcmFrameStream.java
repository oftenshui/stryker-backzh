package com.zalexdev.stryker.hid.backchannel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AcmFrameStream implements Closeable {

    public static final byte[] MAGIC = new byte[]{'S','T','R','K'};

    private static volatile long REAP_SETTLE_MS = 50L;

    public static void setReapSettleMs(long ms) {
        if (ms < 0) ms = 0;
        if (ms > 2000) ms = 2000;
        REAP_SETTLE_MS = ms;
    }

    public static long getReapSettleMs() {
        return REAP_SETTLE_MS;
    }

    public interface Listener {
        void onFrame(@NonNull byte[] jpeg, long timestampMs, int hostW, int hostH);
        void onClosed(@Nullable Throwable cause);
    }

    private final Process reader;
    private final BufferedInputStream stream;
    private final Thread pumpThread;
    private volatile boolean closed;

    private AcmFrameStream(Process reader, BufferedInputStream stream, Thread pumpThread) {
        this.reader = reader;
        this.stream = stream;
        this.pumpThread = pumpThread;
    }

    @NonNull
    public static AcmFrameStream open(@NonNull Listener listener) throws IOException {
        return open(AcmChannel.DEFAULT_NODE, listener);
    }

    @NonNull
    public static AcmFrameStream open(@NonNull String nodePath, @NonNull Listener listener)
            throws IOException {
        reapOrphanReaders(nodePath);
        configureRaw(nodePath);
        Process p = Runtime.getRuntime().exec("su");
        p.getOutputStream().write(("exec cat " + shellQuote(nodePath) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().flush();

        BufferedInputStream bin = new BufferedInputStream(p.getInputStream(), 256 * 1024);

        Thread t = new Thread(() -> pump(bin, listener), "stryker-frame-rx");
        t.setDaemon(true);
        AcmFrameStream s = new AcmFrameStream(p, bin, t);
        t.start();
        return s;
    }

    @Override
    public void close() {
        closed = true;
        try { stream.close(); } catch (Exception ignored) {}
        try { reader.destroy(); } catch (Exception ignored) {}
        if (pumpThread != null) pumpThread.interrupt();
    }

    private static void pump(BufferedInputStream in, Listener listener) {
        byte[] header = new byte[20];
        Throwable cause = null;
        try {
            while (true) {
                if (!readFully(in, header, 0, 4)) return;
                while (!matchesMagic(header)) {
                    System.arraycopy(header, 1, header, 0, 3);
                    int b = in.read();
                    if (b < 0) return;
                    header[3] = (byte) b;
                }
                if (!readFully(in, header, 4, 16)) return;
                int len = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16)
                        | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
                long ts = ((long)(header[8]  & 0xFF) << 56) | ((long)(header[9]  & 0xFF) << 48)
                        | ((long)(header[10] & 0xFF) << 40) | ((long)(header[11] & 0xFF) << 32)
                        | ((long)(header[12] & 0xFF) << 24) | ((long)(header[13] & 0xFF) << 16)
                        | ((long)(header[14] & 0xFF) << 8)  |  (long)(header[15] & 0xFF);
                int hostW = ((header[16] & 0xFF) << 8) | (header[17] & 0xFF);
                int hostH = ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                if (len <= 0 || len > 8 * 1024 * 1024) {
                    continue;
                }
                byte[] payload = new byte[len];
                if (!readFully(in, payload, 0, len)) return;
                listener.onFrame(payload, ts, hostW, hostH);
            }
        } catch (Throwable t) {
            cause = t;
        } finally {
            listener.onClosed(cause);
        }
    }

    private static boolean matchesMagic(byte[] buf) {
        return buf[0] == MAGIC[0] && buf[1] == MAGIC[1]
            && buf[2] == MAGIC[2] && buf[3] == MAGIC[3];
    }

    private static boolean readFully(InputStream in, byte[] buf, int off, int len)
            throws IOException {
        int remaining = len;
        int cursor = off;
        while (remaining > 0) {
            int n = in.read(buf, cursor, remaining);
            if (n < 0) return false;
            cursor    += n;
            remaining -= n;
        }
        return true;
    }

    private static void reapOrphanReaders(String nodePath) {
        if (REAP_SETTLE_MS == 0) {
            return;
        }
        try {
            Process p = Runtime.getRuntime().exec("su");
            double settleSeconds = REAP_SETTLE_MS / 1000.0;
            p.getOutputStream().write(
                    ("pkill -f 'cat " + nodePath + "' 2>/dev/null; "
                   + "sleep " + String.format(java.util.Locale.US, "%.3f", settleSeconds)
                   + "; exit\n").getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().flush();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void configureRaw(String nodePath) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            p.getOutputStream().write(
                    ("chmod 0666 " + shellQuote(nodePath) + " 2>/dev/null; "
                   + "stty -F " + shellQuote(nodePath)
                            + " 115200 cs8 -cstopb -parenb raw -echo -ixon -ixoff -icanon -isig"
                            + " clocal -hupcl -crtscts"
                            + " 2>/dev/null; exit\n").getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().flush();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
