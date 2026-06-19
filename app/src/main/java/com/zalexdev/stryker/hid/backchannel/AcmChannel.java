package com.zalexdev.stryker.hid.backchannel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public final class AcmChannel implements Closeable {

    public static final String DEFAULT_NODE = "/dev/ttyGS0";

    public interface Listener {
        void onLine(@NonNull String line);
        void onClosed(@Nullable Throwable cause);
    }

    public static final class Stats {
        public final long bytesIn;
        public final long bytesOut;
        public final long openedAtNanos;
        public final long sampledAtNanos;

        Stats(long bytesIn, long bytesOut, long openedAtNanos) {
            this.bytesIn = bytesIn;
            this.bytesOut = bytesOut;
            this.openedAtNanos = openedAtNanos;
            this.sampledAtNanos = System.nanoTime();
        }
    }

    private final String nodePath;
    private final Process reader;
    private final Process writer;
    private final OutputStream writerStdin;
    private final Thread readerThread;
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();
    private final long openedAtNanos = System.nanoTime();
    private volatile boolean closed;

    private AcmChannel(String nodePath,
                       Process reader,
                       Process writer,
                       OutputStream writerStdin,
                       Thread readerThread) {
        this.nodePath = nodePath;
        this.reader = reader;
        this.writer = writer;
        this.writerStdin = writerStdin;
        this.readerThread = readerThread;
    }

    @NonNull
    public static AcmChannel open(@NonNull Listener listener) throws IOException {
        return open(DEFAULT_NODE, listener);
    }

    @NonNull
    public static AcmChannel open(@NonNull String nodePath, @NonNull Listener listener) throws IOException {
        configureRaw(nodePath);

        Process rp = Runtime.getRuntime().exec("su");
        rp.getOutputStream().write(("exec cat " + shellQuote(nodePath) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        rp.getOutputStream().flush();

        Process wp = Runtime.getRuntime().exec("su");
        OutputStream wIn = wp.getOutputStream();
        wIn.write(("exec cat > " + shellQuote(nodePath) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        wIn.flush();

        AcmChannel[] holder = new AcmChannel[1];
        Thread t = new Thread(() -> pump(rp, listener, holder), "stryker-acm-rx");
        t.setDaemon(true);
        AcmChannel ch = new AcmChannel(nodePath, rp, wp, wIn, t);
        holder[0] = ch;
        t.start();
        return ch;
    }

    public void writeLine(@NonNull String line) throws IOException {
        write((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    public void write(@NonNull byte[] data) throws IOException {
        if (closed) throw new IOException("AcmChannel is closed");
        writerStdin.write(data);
        writerStdin.flush();
        bytesOut.addAndGet(data.length);
    }

    @NonNull
    public Stats stats() {
        return new Stats(bytesIn.get(), bytesOut.get(), openedAtNanos);
    }

    public String getNodePath() {
        return nodePath;
    }

    @Override
    public void close() {
        closed = true;
        try { writerStdin.close(); } catch (Exception ignored) {}
        try { reader.destroy(); } catch (Exception ignored) {}
        try { writer.destroy(); } catch (Exception ignored) {}
        if (readerThread != null) readerThread.interrupt();
    }

    private static void pump(Process p, Listener listener, AcmChannel[] holder) {
        Throwable cause = null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                AcmChannel ch = holder[0];
                if (ch != null) {
                    ch.bytesIn.addAndGet(line.length() + 1L);
                }
                listener.onLine(line);
            }
        } catch (Throwable t) {
            cause = t;
        }
        listener.onClosed(cause);
    }

    private static void configureRaw(String nodePath) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            p.getOutputStream().write(
                    ("stty -F " + shellQuote(nodePath)
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
