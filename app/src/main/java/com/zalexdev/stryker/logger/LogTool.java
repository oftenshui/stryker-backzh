package com.zalexdev.stryker.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class LogTool {

    public static final String SHELL = "shell";
    public static final String CORE = "core";

    private LogTool() {}

    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("airodump-ng", "wifi");
        KEYWORDS.put("aireplay-ng", "wifi");
        KEYWORDS.put("airmon-ng", "wifi");
        KEYWORDS.put("airbase-ng", "wifi");
        KEYWORDS.put("aircrack-ng", "aircrack");
        KEYWORDS.put("besside-ng", "wifi");
        KEYWORDS.put("wpa_supplicant", "wifi");
        KEYWORDS.put("hcxdumptool", "wifi");
        KEYWORDS.put("hcxpcapngtool", "wifi");
        KEYWORDS.put("mdk4", "mdk4");
        KEYWORDS.put("mdk3", "mdk4");
        KEYWORDS.put("wpspin", "wps");
        KEYWORDS.put("reaver", "wps");
        KEYWORDS.put("wash", "wps");
        KEYWORDS.put("bully", "wps");
        KEYWORDS.put("pixiewps", "wps");
        KEYWORDS.put("nmap", "nmap");
        KEYWORDS.put("masscan", "nmap");
        KEYWORDS.put("nuclei", "nuclei");
        KEYWORDS.put("searchsploit", "searchsploit");
        KEYWORDS.put("hydra", "hydra");
        KEYWORDS.put("medusa", "hydra");
        KEYWORDS.put("msfconsole", "metasploit");
        KEYWORDS.put("msfvenom", "metasploit");
        KEYWORDS.put("msfdb", "metasploit");
        KEYWORDS.put("msfrpcd", "metasploit");
        KEYWORDS.put("msfd", "metasploit");
        KEYWORDS.put("msfpc", "metasploit");
        KEYWORDS.put("cameradar", "camera");
        KEYWORDS.put("radar", "camera");
        KEYWORDS.put("macchanger", "macchanger");
        KEYWORDS.put("changemac", "macchanger");
        KEYWORDS.put("x11vnc", "vnc");
        KEYWORDS.put("vncserver", "vnc");
        KEYWORDS.put("xfce4-session", "vnc");
        KEYWORDS.put("startxfce4", "vnc");
        KEYWORDS.put("xvfb", "vnc");
        KEYWORDS.put("apk", "apk");
        KEYWORDS.put("apt", "apk");
        KEYWORDS.put("pip", "apk");
        KEYWORDS.put("iwconfig", "iface");
        KEYWORDS.put("iwlist", "iface");
        KEYWORDS.put("ifconfig", "iface");
        KEYWORDS.put("iw", "iface");
        KEYWORDS.put("rfkill", "iface");
        KEYWORDS.put("svc", "android");
        KEYWORDS.put("pm", "android");
        KEYWORDS.put("settings", "android");
        KEYWORDS.put("getprop", "android");
        KEYWORDS.put("setprop", "android");
        KEYWORDS.put("dumpsys", "android");
    }

    private static boolean isCoreBuiltin(String token) {
        switch (token) {
            case "[":
            case "test":
            case "ls":
            case "cat":
            case "echo":
            case "cp":
            case "mv":
            case "rm":
            case "mkdir":
            case "chmod":
            case "chown":
            case "touch":
            case "ln":
            case "id":
            case "ps":
            case "kill":
            case "killall":
            case "pkill":
            case "sleep":
            case "sync":
            case "mount":
            case "umount":
            case "sysctl":
            case "mknod":
            case "sqlite3":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    public static String classify(@Nullable String command) {
        if (command == null) return SHELL;
        String c = command.trim();
        if (c.isEmpty()) return SHELL;

        c = stripPrefix(c, "executing chroot command:");
        c = stripPrefix(c, "executing command:");
        c = stripPrefix(c, "command:");
        c = c.trim();

        String lower = c.toLowerCase(Locale.US);

        for (Map.Entry<String, String> e : KEYWORDS.entrySet()) {
            if (containsToken(lower, e.getKey())) {
                return e.getValue();
            }
        }

        String token = firstMeaningfulToken(c);
        if (token == null || token.isEmpty()) return SHELL;
        if (isCoreBuiltin(token)) return CORE;
        return token;
    }

    private static String stripPrefix(String c, String lowerPrefix) {
        if (c.length() >= lowerPrefix.length()
                && c.substring(0, lowerPrefix.length()).toLowerCase(Locale.US).equals(lowerPrefix)) {
            return c.substring(lowerPrefix.length());
        }
        return c;
    }

    private static boolean containsToken(String haystack, String word) {
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(word, from);
            if (idx < 0) return false;
            boolean leftOk = idx == 0 || !isWordChar(haystack.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightOk = end >= haystack.length() || !isWordChar(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            from = idx + 1;
        }
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '-';
    }

    private static String firstMeaningfulToken(String c) {
        String[] parts = c.split("\\s+");
        for (String raw : parts) {
            if (raw.isEmpty()) continue;
            if (raw.indexOf('=') > 0 && !raw.startsWith("-")) continue;
            if (raw.startsWith("-")) continue;
            String base = basename(raw);
            String low = base.toLowerCase(Locale.US);
            switch (low) {
                case "su":
                case "sudo":
                case "sh":
                case "ash":
                case "bash":
                case "busybox":
                case "chroot":
                case "env":
                case "chroot_exec":
                case "ash_exec":
                case "stryker-ch":
                    continue;
                default:
                    return low;
            }
        }
        return null;
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        String base = slash >= 0 ? path.substring(slash + 1) : path;
        base = base.replaceAll("^['\"(]+", "").replaceAll("['\")]+$", "");
        return base;
    }
}
