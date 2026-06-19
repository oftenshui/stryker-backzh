package com.zalexdev.stryker.appintro.install;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LogClassifier {

    private static final Pattern GO_FILE_LINE = Pattern.compile("\\.go:\\d+");
    private static final Pattern MODULE_PATH = Pattern.compile("^[A-Za-z0-9._~-]+(/[A-Za-z0-9._~+-]+)+$");

    private LogClassifier() {
    }

    public static String strip(String line) {
        if (line == null) {
            return "";
        }
        String t = line.trim();
        if (t.length() >= 3) {
            String head = t.substring(0, 3);
            if (head.equals("[E]") || head.equals("[O]") || head.equals("[W]") || head.equals("[I]")) {
                return t.substring(3).trim();
            }
        }
        return t;
    }

    public static LogLevel classify(String content) {
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) {
            return LogLevel.INFO;
        }
        if (c.startsWith("OK:")) {
            return LogLevel.SUCCESS;
        }
        if (MODULE_PATH.matcher(c).matches()) {
            return LogLevel.INFO;
        }
        String lower = c.toLowerCase(Locale.ROOT);
        if (isError(c, lower)) {
            return LogLevel.ERROR;
        }
        if (isWarning(c, lower)) {
            return LogLevel.WARN;
        }
        if (isProgress(lower)) {
            return LogLevel.STEP;
        }
        return LogLevel.INFO;
    }

    private static boolean isError(String c, String lower) {
        if (c.contains("FATAL") || c.contains("ERROR") || c.contains("Errno")) {
            return true;
        }
        if (lower.startsWith("fatal:") || lower.startsWith("apk: ")) {
            return true;
        }
        if (lower.contains("error: ") || lower.contains(": error") || lower.contains("error:")) {
            return true;
        }
        if (lower.contains("no such file")
                || lower.contains("permission denied")
                || lower.contains("no space left")
                || lower.contains("cannot find package")
                || lower.contains("no required module provides")
                || lower.contains("build failed")
                || lower.contains("undefined:")
                || lower.contains("not in std")
                || lower.contains("not found")
                || lower.contains("checksum mismatch")
                || lower.contains("connection refused")
                || lower.contains("could not resolve")
                || lower.contains("name resolution")
                || lower.contains("network is unreachable")
                || lower.contains("i/o timeout")
                || lower.contains("handshake timeout")
                || lower.contains("unable to ")
                || lower.contains("failed to ")) {
            return true;
        }
        return GO_FILE_LINE.matcher(c).find();
    }

    private static boolean isWarning(String c, String lower) {
        return c.startsWith("WARNING")
                || lower.startsWith("warning")
                || lower.contains("warning:")
                || lower.contains("deprecated");
    }

    private static boolean isProgress(String lower) {
        return lower.startsWith("go: downloading")
                || lower.startsWith("go: finding")
                || lower.startsWith("go: extracting")
                || lower.startsWith("fetch ")
                || lower.startsWith("fetching")
                || lower.startsWith("cloning")
                || lower.startsWith("receiving")
                || lower.startsWith("resolving")
                || lower.startsWith("counting")
                || lower.startsWith("compressing")
                || lower.startsWith("unpacking")
                || lower.startsWith("downloading");
    }
}
