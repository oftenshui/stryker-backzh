package com.zalexdev.stryker.routerscan.utils;

import android.content.Context;
import android.util.Log;

import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

public final class RsRunner {

    private RsRunner() { }

    public static Router run(Context ctx, String ip) {
        AuthLists.ensureDeployed(ctx);
        Router r = new Router();
        r.setIp(ip);
        r.setSuccess(false);

        RouterScanLog.info(ctx, ip, "spawn rs " + ip);
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = proc.getOutputStream();
            InputStream stderr = proc.getErrorStream();
            InputStream stdout = proc.getInputStream();
            String cmd = "rs " + ip
                    + " /sdcard/Stryker/rs/auth_basic.txt"
                    + " /sdcard/Stryker/rs/auth_digest.txt"
                    + " /sdcard/Stryker/rs/auth_form.txt";
            stdin.write((Core.EXECUTE + "'" + cmd + "'" + '\n').getBytes());
            stdin.write("\n".getBytes());
            stdin.flush();
            stdin.close();

            ArrayList<String> out = new ArrayList<>();
            String line;
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                out.add(line);
                RouterScanLog.append(ctx, ip, "OUT", line);
            }
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                RouterScanLog.append(ctx, ip, "ERR", line);
            }
            br.close();
            proc.waitFor();
            r = parse(out, ip);
            RouterScanLog.info(ctx, ip, r.getSuccess() ? "cracked" : "no match");
        } catch (Exception e) {
            Log.d("RsRunner", "rs " + ip + " failed: " + e.getMessage());
        } finally {
            if (proc != null) {
                try { proc.destroy(); } catch (Exception ignored) { }
            }
        }
        return r;
    }

    private static Router parse(ArrayList<String> output, String ip) {
        Router result = new Router();
        result.setSuccess(false);
        result.setIp(ip);
        for (String temp : output) {
            if (temp.contains("SSID:") && !temp.contains("BSSID:")) {
                result.setSsid(temp.replace("SSID: ", ""));
                result.setSuccess(true);
            } else if (temp.contains("Auth:")) {
                result.setAuth(temp.replace("Auth: ", ""));
                result.setSuccess(true);
            } else if (temp.contains("Key:")) {
                result.setPsk(temp.replace("Key: ", ""));
                result.setSuccess(true);
            } else if (temp.contains("WPS:")) {
                result.setWps(temp.replace("WPS: ", ""));
                result.setSuccess(true);
            } else if (temp.contains("Title:")) {
                result.setTitle(temp.replace("Title: ", ""));
            } else if (temp.contains("BSSID:")) {
                result.setBssid(temp.replace("BSSID: ", ""));
            }
            if (result.getSuccess()) {
                result.setStatus("Success");
                result.setType(1);
            }
        }
        return result;
    }
}
