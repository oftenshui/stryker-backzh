package com.zalexdev.stryker.utils;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.Context.VIBRATOR_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Environment.getExternalStorageDirectory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.race604.drawable.wave.WaveDrawable;
import com.zalexdev.stryker.BuildConfig;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.Credentials;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.Exploit;
import com.zalexdev.stryker.custom.Module;
import com.zalexdev.stryker.custom.Router;
import com.zalexdev.stryker.custom.Site;
import com.zalexdev.stryker.logger.LogTool;
import com.zalexdev.stryker.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;


public class Core {

    public final static String EXECUTE = "/data/data/com.zalexdev.stryker/files/chroot_exec ";
    public final static String BUSYBOX = "/data/data/com.zalexdev.stryker/files/busybox ";
    public final static String HIDDEN_MAC = "XX:XX:XX:XX:XX:XX";
    public final String versionName = BuildConfig.VERSION_NAME;
    public final int versionInt = BuildConfig.VERSION_CODE;
    public HashMap<String, String> vendorDB = new HashMap<String, String>();
    private final SharedPreferences preferences;
    public Context context;
    public Process process;
    public MonitorManager monitorManager;
    public Logger logger;
    public SQLiteDatabase db;
    public SQLiteDatabase dbСodename;
    public SQLiteDatabase dbAdapters;
    public Core(Context context) {

        SharedPreferences preferences1;
        this.context = context;
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                    preferences1 =  EncryptedSharedPreferences.create(
                    "touchMeAndGetPhoneReset",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                    preferences1 =  EncryptedSharedPreferences.create(
                    "touchMeAndGetPhoneReset",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ); } catch (GeneralSecurityException | IOException ex) {
            ex.printStackTrace();
            preferences1 = PreferenceManager.getDefaultSharedPreferences(context);
        }}
        preferences = preferences1;
        logger = new Logger();
        monitorManager = new MonitorManager(this);
    }

    public Context getContext() {
        return context;
    }

    public int connectWiFi2(String ssid, String psk){
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);
        wifiConfig.preSharedKey = String.format("\"%s\"",psk);
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int netId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
        return netId;
    }

    public void deleteWifi(int netid){
        WifiManager wifiManager = (WifiManager)context.getSystemService(WIFI_SERVICE);
        wifiManager.removeNetwork(netid);
    }

    public void saveNetwork(String bssid, String psk, String pin, String ssid){
        ArrayList<String> nw = new ArrayList<>();
        nw.add(psk);
        nw.add(pin);
        nw.add(ssid);
        nw.add(bssid);
        putListString(bssid,nw);
        addSavedNetwork(bssid);
    }
    public ArrayList<ArrayList<String>> getSavedNetworks(){
        ArrayList<String> bssids = getListString("bssids");
        ArrayList<ArrayList<String>> networks = new ArrayList<>();
        for (String bssid : bssids) {
            networks.add(getListString(bssid));
        }
        return networks;
    }

    public void addSavedNetwork(String bssid){
        ArrayList<String> bssids = getListString("bssids");
        bssids.add(bssid);
        putListString("bssids",bssids);
    }
    public void removeSavedNetwork(String bssid){
        ArrayList<String> bssids = getListString("bssids");
        bssids.remove(bssid);
        putListString("bssids",bssids);
        remove(bssid);
    }

    public void copyToClipBoard(String s){
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Stryker", s);
        clipboard.setPrimaryClip(clip);
    }
    public ArrayList<String> getNetwork(String bssid){
        return getListString(bssid);
    }


    public int getInt(String key) {
        return preferences.getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }


    public String getString(String key) {
        return preferences.getString(key, "");
    }
    public ArrayList<String> getListString(String key) {
        return new ArrayList<>(Arrays.asList(TextUtils.split(preferences.getString(key, ""), "‚‗‚")));
    }


    public boolean getBoolean(String key) {
        if (preferences != null){
            return preferences.getBoolean(key, false);}else{
            return false;
        }
    }

    public void putInt(String key, int value) {
        isNull(key);
        preferences.edit().putInt(key, value).apply();
    }


    public void putString(String key, String value) {
        isNull(key);
        preferences.edit().putString(key, value).apply();
    }

    public void putListString(String key, ArrayList<String> stringList) {
        isNull(key);
        String[] myStringList = stringList.toArray(new String[stringList.size()]);
        preferences.edit().putString(key, TextUtils.join("‚‗‚", myStringList)).apply();
    }

    public void putBoolean(String key, boolean value) {
        isNull(key);
        preferences.edit().putBoolean(key, value).apply();
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private void isNull(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
    }


    public Logger getLogger() {
        return logger;
    }

    public void toaster(String msg) {
        Toast toast = Toast.makeText(context,
                msg, Toast.LENGTH_SHORT);
        toast.show();
    }
    public void toaster(Activity activity,String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(activity,
                        msg, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

    }





    public void saveResult(ArrayList<Router> rs) {
        if (rs == null || rs.isEmpty()) return;

        StringBuilder body = new StringBuilder();
        for (Router r : rs) {
            if (!r.getSuccess()) continue;
            body.append('"').append(safeCsv(r.getIp())).append("\";\"80\";\"100\";\"Done\";\"")
                    .append(safeCsv(r.getAuth())).append("\";\"")
                    .append(safeCsv(r.getTitle())).append("\";\" \";\" \";\"")
                    .append(safeCsv(r.getBssid())).append("\";\"")
                    .append(safeCsv(r.getSsid())).append("\";\" \";\"")
                    .append(safeCsv(r.getPsk())).append("\";\"")
                    .append(safeCsv(r.getWps()))
                    .append("\";\" \";\" \";\" \";\" \";\" \";\" \";\" \";\" \";\" \";\" \";\r\n");
        }
        if (body.length() == 0) return;

        final String target = "/sdcard/Stryker/routerscan.csv";
        if (!checkFile(target)) {
            body.insert(0, "\"IP Address\";\"Port\";\"Time (ms)\";\"Status\";\"Authorization\";"
                    + "\"Server name / Realm name / Device type\";\"Radio Off\";\"Hidden\";"
                    + "\"BSSID\";\"ESSID\";\"Security\";\"Key\";\"WPS PIN\";\"LAN IP Address\";"
                    + "\"LAN Subnet Mask\";\"WAN IP Address\";\"WAN Subnet Mask\";\"WAN Gateway\";"
                    + "\"Domain Name Servers\";\"Latitude\";\"Longitude\";\"Comments\"\r\n");
        }

        File staged = new File(context.getFilesDir(), "routerscan_append.csv");
        try (FileOutputStream fos = new FileOutputStream(staged, false);
             OutputStreamWriter osw = new OutputStreamWriter(fos)) {
            osw.write(body.toString());
            osw.flush();
        } catch (IOException e) {
            Log.e("Core.saveResult", "Could not stage CSV: " + e.getMessage());
            return;
        }

        customCommand("mkdir -p '/sdcard/Stryker'");
        customCommand("touch '" + target + "'");
        customCommand("cat '" + staged.getAbsolutePath() + "' >> '" + target + "'");
        customCommand("chmod 0666 '" + target + "'");
        staged.delete();
    }

    private static String safeCsv(String s) {
        if (s == null) return " ";
        return s.replace("\"", "''");
    }

    public void vibrate(int mil) {
        if (SDK_INT >= 26) {
            ((Vibrator) context.getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(mil, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            ((Vibrator) context.getSystemService(VIBRATOR_SERVICE)).vibrate(mil);
        }
    }

    public ArrayList<String> getListFiles(String parentDir) {
        return customCommand("ls "+parentDir);
    }


    public void saveExploit(Exploit exploit){
        ArrayList<String> exploits = getListString("exploits");
        exploits.add(parseExploit(exploit));
        putListString("exploits",exploits);
    }
    public String parseExploit(Exploit exploit){
        JSONObject exp = new JSONObject();
        try {
            exp.put("title",exploit.getTitle());
            exp.put("path",exploit.getPath());
            exp.put("pattern",exploit.getSuccesspatern());
            exp.put("lang",exploit.getLang());
            exp.put("args",exploit.getArgs());
            exp.put("issys",exploit.getIssystem());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return exp.toString();
    }

    public Exploit getExploitByTitle(String title){
        Exploit t = new Exploit();
        ArrayList<Exploit> exploits = getExploits();
        for (Exploit e: exploits){
            if (e.getTitle().equals(title)){
                t = e;
                break;
            }
        }
        return t;
    }
    public void updateExploits(){
        deleteFile("/data/local/stryker/release/exploits");
        copyFile("/storage/emulated/0/Stryker/exploits"," /data/local/stryker/release/exploits");
        chmodFolder("/data/local/stryker/release/exploits");
    }

    public void deleteExploit(int id){
        ArrayList<String> exploits = getListString("exploits");
        exploits.remove(id);
        putListString("exploits",exploits);
    }
    public Exploit unparseExploit(String exploitstring){
        Exploit exploit = new Exploit();
        try {
            JSONObject exp = new JSONObject(exploitstring);
            exploit.setTitle(exp.getString("title"));
            exploit.setPath(exp.getString("path"));
            exploit.setSuccesspatern(exp.getString("pattern"));
            exploit.setLang(exp.getString("lang"));
            exploit.setArgs(exp.getString("args"));
            exploit.setIssystem(exp.getBoolean("issys"));
        } catch (JSONException e) {e.printStackTrace();}
        return exploit;
    }
    public ArrayList<Exploit> getExploits(){
        ArrayList<Exploit> list= new ArrayList<>();
        ArrayList<String> exploits = getListString("exploits");
        for (String e : exploits){
            list.add(unparseExploit(e));
        }
        if (!exploits.toString().contains("EternalBlue")){
            Exploit eternal = new Exploit();
            eternal.setTitle("EternalBlue");
            eternal.setPath("eternalscan.py");
            eternal.setArgs(" {IP}");
            eternal.setIssystem(true);
            eternal.setSuccesspatern("VUNLFOUNDED");
            eternal.setLang("Python");
            saveExploit(eternal);
        }
        if (!exploits.toString().contains("SMBGhost")){
            Exploit ghost = new Exploit();
            ghost.setTitle("SMBGhost");
            ghost.setPath("ghostscanner.py");
            ghost.setArgs(" {IP}");
            ghost.setIssystem(true);
            ghost.setSuccesspatern("VUNLFOUNDED");
            ghost.setLang("Python");
            saveExploit(ghost);
        }
        if (!exploits.toString().contains("Bluekeep")){
            Exploit blue = new Exploit();
            blue.setTitle("Bluekeep");
            blue.setPath("bluekeepscan.py");
            blue.setArgs(" {IP}");
            blue.setIssystem(true);
            blue.setSuccesspatern("VULNERABLE");
            blue.setLang("Python");
            saveExploit(blue);
        }
        if (!exploits.toString().contains("CVE-2022-27255")){
            Exploit cve = new Exploit();
            cve.setTitle("CVE-2022-27255");
            cve.setPath("checker.py");
            cve.setArgs(" {IP} {PORT}");
            cve.setIssystem(true);
            cve.setSuccesspatern("Target vulnerable");
            cve.setLang("Python");
            saveExploit(cve);
        }
        return list;
    }

    public boolean is64Bit() {
        return (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0);
    }
    public void scale(View v, Float x){
        v.animate().scaleY(x);
        v.animate().scaleX(x);
    }

    @Deprecated
    public String getDeviceNameByPid(String vidPid) {
        if (vidPid == null || !vidPid.contains(":")) return "Unknown";
        String[] parts = vidPid.split(":");
        String raw = com.zalexdev.stryker.netdetect.LegacyDeviceDb.lookupRaw(context, parts[0], parts[1]);
        return raw != null ? raw : "Unknown";
    }

    public String getStorage() {
        return getExternalStorageDirectory().getAbsolutePath() + "/";
    }
    public boolean checkModel(String model){
        BufferedReader reader;
        boolean result = false;
        try {
            reader = new BufferedReader(new InputStreamReader(context.getAssets().open("routes.txt")));
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                if(model.toLowerCase(Locale.ROOT).contains(mLine.toLowerCase(Locale.ROOT))){
                    result = true;
                }
            }
            for (String m : getRouters()){
                if (model.toLowerCase(Locale.ROOT).contains(m.toLowerCase(Locale.ROOT))){
                    result = true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void installApplication(Context context, String filePath) {
        logger.writeLine("Installing update",1);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriFromFile(context, new File(filePath)), "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }


    private static Uri uriFromFile(Context context, File file) {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
    }

    public ArrayList<Module> getModules(){
        return new ArrayList<>();
    }
    public void installModule(String name){
        logger.writeLine("Installing "+name,1);
        ArrayList<String> mods = getListString("installed_modules");
        mods.add(name);
        putListString("installed_modules",mods);
    }
    public boolean checkModule(String name){
        logger.writeLine("Checking if "+name+" is installed",1);
        ArrayList<String> mods = getListString("installed_modules");
        return mods.contains(name);
    }
    public void deleteModule(String name){
        logger.writeLine("Deleting module: "+name,1);
        ArrayList<String> mods = getListString("installed_modules");
        mods.remove(name);
        putListString("installed_modules",mods);
    }

    public boolean isToolInstalled(String tool){
        switch (tool) {
            case "metasploit": return getBoolean("msf") || checkFile("/data/local/stryker/release/metasploit-framework/msfconsole");
            case "nuclei": return getBoolean("nuclei") || checkFile("/data/local/stryker/release/usr/bin/nuclei");
            case "hydra": return getBoolean("hydra") || checkFile("/data/local/stryker/release/usr/bin/hydra");
            case "searchsploit": return checkFile("/data/local/stryker/release/exploitdb/searchsploit");
            default: return false;
        }
    }

    public boolean uninstallTool(String tool){
        logger.writeLine("Uninstalling tool: "+tool,1);
        switch (tool) {
            case "metasploit":
                deleteFile("/data/local/stryker/release/metasploit-framework");
                deleteFile("/data/local/stryker/release/msfpc");
                deleteFile("/data/local/stryker/release/usr/bin/msfvenom");
                putBoolean("msf", false);
                break;
            case "nuclei":
                deleteFile("/data/local/stryker/release/usr/bin/nuclei");
                deleteFile("/data/local/stryker/release/root/go/bin/nuclei");
                putBoolean("nuclei", false);
                break;
            case "hydra":
                customChrootCommand("apk del hydra", true);
                deleteFile("/data/local/stryker/release/usr/bin/hydra");
                putBoolean("hydra", false);
                break;
            case "searchsploit":
                deleteFile("/data/local/stryker/release/exploitdb");
                break;
            default:
                return false;
        }
        deleteModule(tool);
        remove("install_status_" + tool);
        return !isToolInstalled(tool);
    }
    public boolean unzip(String zipFile, String targetDirectory)  {
        customCommand(BUSYBOX+"unzip -o "+zipFile+" -d "+targetDirectory);
        return checkFolder(targetDirectory);

    }
    public Boolean mountCore(){
        customMegaCommand("/data/data/com.zalexdev.stryker/files/bootroot");
        return checkFolder("/data/local/stryker/release/sdcard/Stryker");
    }
    public Boolean unmountCore(){
        customMegaCommand("/data/data/com.zalexdev.stryker/files/killroot");
        return !checkFolder("/data/local/stryker/release/sdcard/Stryker");
    }
    public Boolean isMounted(){
        return checkFolder("/data/local/stryker/release/sdcard/Stryker");
    }
    public boolean isOldMounted(){
        return checkFolder("/data/local/stryker/beta/sdcard/Stryker");
    }
    public boolean ping(String ip, int port,int timeout) {
        try {
            URI uri;
            if(port !=443){uri = URI.create("http://" + ip + ":" + port + "/");}else{uri = URI.create("https://" + ip + "/"); }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(connection::disconnect, timeout + 4000);
            return connection.getResponseCode() >= 200 && !(connection.getResponseCode() == 404) && !(connection.getResponseCode() == 403);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean ping(String ip, int timeout) {
        try {
            URI uri;
            uri = URI.create("https://" + ip + "/");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(connection::disconnect, timeout + 4000);
            return connection.getResponseCode() >= 200 && !(connection.getResponseCode() == 404) && !(connection.getResponseCode() == 403);
        } catch (Exception e) {
            return false;
        }
    }

    public void moveNext(ViewPager2 mPager) {
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
    }


    public void movePrevious(ViewPager mPager) {
        mPager.setCurrentItem(mPager.getCurrentItem() - 1);
    }
    public boolean isInstalledOnSdCard() {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo ai = pi.applicationInfo;
            return (ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        return false;
    }


    public void reCreateProcess(){
        try {
            if (process != null) {
                process.getOutputStream().write("exit\nexit\n".getBytes());
                process.getOutputStream().flush();
                process.destroy();}
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Process generateSuProcess(){
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();

            try {
                process = Runtime.getRuntime().exec("echo Device is not rooted");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return  process;
    }


    public boolean checkFile(String path){
        logger.writeLine("Checking file "+path,1);
        return customCommand("[ -f " + path + " ] && echo true || echo false").contains("true");
    }

    public boolean checkFolder(String path){
        boolean ok = false;
        logger.writeLine("Checking folder "+path,1);
        for (String s : customCommand("[ -d " + path + " ] && echo true || echo false")) {
            if (s.contains("true")) {
                ok = true;
                break;
            }
        }
        return ok;
    }
    public boolean checkMagiskNotification(){
        reCreateProcess();
        if (!getBoolean("offed")){
        String cmd = "/data/data/com.zalexdev.stryker/files/sqlite3 /data/adb/magisk.db \"SELECT notification FROM policies WHERE package_name='com.zalexdev.stryker';\"";
        boolean b = Core.contains(customCommand(cmd),"1");
        if (!b) {
            cmd = "/data/data/com.zalexdev.stryker/files/sqlite3 /data/adb/magisk.db \"SELECT notification FROM policies WHERE uid='"+android.os.Process.myUid()+"';\"";
            b = Core.contains(customCommand(cmd),"1");
        }
        return b;}else{
            return false;
        }
    }

    public boolean checkRoot(){
        return contains(customCommand("id"),"uid=0");
    }


    public String executeCommand(String command){
        StringBuilder result = new StringBuilder();
        Process process = generateSuProcess();
        String tool = LogTool.classify(command);
        logger.writeLine("Executing command: " + command,1, tool);
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\nexit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {
                logger.writeLine(line,2, tool);
                result.append(line).append("\n");
            }
            br.close();
            if (result.length() > 0)
                result = new StringBuilder(result.substring(0, result.length() - 1));
        } catch (IOException e) {
        }
        process.destroy();
        return result.toString();
    }
    public ArrayList<String> customCommand(String command){
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        String tool = LogTool.classify(command);
        logger.writeLine("Executing command: " + command,1, tool);
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\nexit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {
                logger.writeLine(line,2, tool);
                result.add(line);
            }
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineError;
            while ((lineError = br2.readLine()) != null) {
                logger.writeLine(lineError,3, tool);
                result.add(lineError);
            }
            br2.close();
        } catch (IOException e) {

        }
        process.destroy();
        return result;
    }
    public ArrayList<String> customCommand(String command,boolean nolog){
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {result.add(line);}
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineerror;
            while ((lineerror = br2.readLine()) != null) {result.add(lineerror);}
            br2.close();

        } catch (IOException e) {

        }
        process.destroy();
        return result;
    }
    public void threadCommand(String cmd){new Thread(() -> customCommand(cmd)).start();}

    public void threadChrootCommand(String cmd){new Thread(() -> customChrootCommand(cmd)).start();}


    public ArrayList<String> customMegaCommand(String command){
        ArrayList<String> result = new ArrayList<>();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -mm");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String tool = LogTool.classify(command);
        logger.writeLine("Executing command: " + command,1, tool);
        try {
            OutputStream stdin = Objects.requireNonNull(process).getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));

            String line;
            while ((line = br.readLine()) != null) {
                logger.writeLine(line,2, tool);
                result.add(line);
            }
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineerror;
            while ((lineerror = br2.readLine()) != null) {
                logger.writeLine(lineerror,3, tool);
                result.add(lineerror);
            }
            br2.close();
        } catch (IOException e) {

        }process.destroy();
        return result;
    }
    public ArrayList<String> customChrootCommand(String command)  {
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        String tool = LogTool.classify(command);
        try {
            logger.writeLine("Executing chroot command: " + command,1, tool);
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((EXECUTE+ "'ash'"+ '\n').getBytes());
            stdin.write((command+ '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {
                result.add(line);
                if (line.contains("no interfaces assigned")) {
                    logger.writeLine("No interfaces assigned. Answering no",3, tool);
                    stdin.write(("n\n").getBytes());
                    stdin.flush();
                }
                logger.writeLine(line,2, tool);
            }
            br.close();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
            String lineerror;
            while ((lineerror = br2.readLine()) != null) {
                logger.writeLine(lineerror,3, tool);
                result.add(lineerror);
            }
            br2.close();
            process.waitFor();
            process.destroy();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public ArrayList<String> customChrootCommand(String command, boolean nolog)  {
        ArrayList<String> result = new ArrayList<>();
        Process process = generateSuProcess();
        try {
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((EXECUTE+ "'ash'"+ '\n').getBytes());
            stdin.write((command+ '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {result.add(line);}
            br.close();
            BufferedReader br2 = new BufferedReader (new InputStreamReader(stderr));
            String lineerror;
            while ((lineerror = br2.readLine()) != null) {result.add(lineerror);}
            br2.close();
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();

        }process.destroy();
        return result;
    }

    public static String busyboxAssetName() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return "busybox64";
        }
        return "busybox32";
    }

    public static void extractBusybox(Context ctx) {
        String asset = busyboxAssetName();
        File out = new File(ctx.getFilesDir(), "busybox");
        try (InputStream in = ctx.getAssets().open(asset);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            os.flush();
        } catch (IOException e) {
            Log.e("Core", "Failed to extract busybox variant " + asset, e);
            return;
        }
        try { out.setExecutable(true, false); } catch (Exception ignored) {}
    }

    public void moveFile(@NonNull String source, @NonNull String destination){
        customCommand("mv " + source + " " + destination);
    }
    public void copyFile(@NonNull String source, @NonNull String destination){
        customCommand("cp -R " + source + " " + destination);

    }
    public void deleteFile(@NonNull String file){
        customCommand("rm -rf " + file);
    }
    public void createFolder(@NonNull String folder){
        customCommand("mkdir " + folder);

    }
    public void chmodFolder(@NonNull String folder){
        customCommand("chmod 777 -R " + folder);

    }
    public boolean checkInet(){
        logger.writeLine("Checking internet connection...",1);
        return customCommand("ping -c 1 8.8.8.8 ; echo $?").contains("0");
    }





    public String getVendorByMacFromDB(String mac){
        String vendor = "";
        try {
            if (db == null || !db.isOpen()){
                db = SQLiteDatabase.openDatabase("/data/data/com.zalexdev.stryker/files/vendors.db", null, SQLiteDatabase.OPEN_READONLY);
            }
            Cursor cursor = db.rawQuery("select MacPrefix,VendorName from macvendor where MacPrefix LIKE '%"+mac.substring(0,8).toUpperCase(Locale.ROOT)+"%' COLLATE NOCASE", null);
            if (cursor.moveToFirst()) {
                vendor = cursor.getString(1);
            }
            cursor.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
        return toTitleCase(vendor);
    }
    public String getDeviceByCodeNameFromDB(String codename){
        String model = "";
        try {
            if (dbСodename == null || !dbСodename.isOpen()){
                dbСodename = SQLiteDatabase.openDatabase("/data/data/com.zalexdev.stryker/files/codenames.db", null, SQLiteDatabase.OPEN_READONLY);
            }
            Cursor cursor = dbСodename.rawQuery("SELECT manufacture,model FROM codename WHERE codename = '"+codename+"';", null);

            if (cursor.moveToFirst()) {
                model = cursor.getString(0)+" "+cursor.getString(1).replace(cursor.getString(0),"");
            }
            cursor.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            return toTitleCase(model);
        } catch (NullPointerException ignored) {
            return model;
        }
    }
    public static String toTitleCase(String givenString) throws NullPointerException {
        String[] arr = givenString.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (s.length() > 1) {
                sb.append(Character.toUpperCase(s.charAt(0)))
                        .append(s.substring(1)).append(" ");
            }
        }
        return sb.toString();
    }
    public static boolean isNumeric(String string) {


        if(string == null || string.equals("")) {
            return false;
        }else{
            try {
                Integer.parseInt(string);
                return true;
            } catch (NumberFormatException e) {
                return false;

            }}
    }
    public void saveLastNetworkScan(ArrayList<Device> devices){
        ArrayList<String> devs = new ArrayList<>();
        for (Device d : devices){
            devs.add(d.toJSON());
        }
        putListString("last_network_scan",devs);
    }
    public ArrayList<Device> getLastNetworkScan(){
        ArrayList<Device> devices = new ArrayList<>();
        ArrayList<String> devs = getListString("last_network_scan");
        for (String d : devs){
            Device device = new Device();
            device.restoreFromJSON(d);
            devices.add(device);
        }
        return devices;
    }
    public ArrayList<String> getLatestIps(){
        ArrayList<Device> devices = getLastNetworkScan();
        ArrayList<String> ips = new ArrayList<>();
        for (Device d : devices){
            ips.add(d.getIp());}
        return ips;
    }

    public String getLocalIpaddress(){
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }
    public boolean isHideEnabled(){
        return getBoolean("hide");
    }
    public boolean isStoreEnabled(){
        return preferences.getBoolean("save_aps", true);
    }
    public boolean isBannerScanEnabled(){
        return preferences.getBoolean("autoBanner", true);
    }

    public boolean isPixieIfaceDown(){
        return preferences.getBoolean("pixie_iface_down", true);
    }

    public String wpsIfaceDownFlag(){
        return isPixieIfaceDown() ? " --iface-down" : "";
    }

    public void wpsDisableWifiIfEnabled(){
        if (isPixieIfaceDown()) customCommand("svc wifi disable");
    }

    public static boolean contains(ArrayList<String> list, String item){
        for (String s : list){if (s.contains(item)){return true;}}
        return false;
    }

    public static String generateString() {return UUID.randomUUID().toString().replace("-", "");}
    public void checkPermission(Activity activity) {
        if (context.checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{WRITE_EXTERNAL_STORAGE},
                    123
            );
        }
    }
    public void openlink(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(browserIntent);
    }
    public ArrayList<Site> getSites(){
        ArrayList<Site> sites = new ArrayList<>();
        ArrayList<String> ss = getListString("sites");
        for (String s : ss){
            sites.add(Site.parseItem(s));
        }
        return sites;
    }
    public void changeSiteByPosition(Site site,int pos){
        ArrayList<String> ss = getListString("sites");
        if (ss.size()>pos ){
        ss.remove(pos);
        ss.add(pos,site.getJSON());
        putListString("sites",ss);}
    }
    public void deleteSiteByPosition(int pos){
        ArrayList<String> ss = getListString("sites");
        ss.remove(pos);

        putListString("sites",ss);
    }
    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }
    public int addSite(Site site){
        ArrayList<String> ss = getListString("sites");
        ss.add(site.getJSON());
        putListString("sites",ss);
        return ss.indexOf(site.getJSON());
    }
    public String getDeviceId(){
        if (getString("device_id").length() >1){
            return getString("device_id");
        }else{
            String id = Build.HARDWARE+Build.MODEL+getRandomNumber(100,9999);
            id = id.replace(" ","");
            putString("device_id",id);
            return id;
        }
    }

    public void putRouters(ArrayList<String> routers){if (routers.size() > 0){ putListString("routers",routers);}}

    public ArrayList<String> getRouters(){
        return getListString("routers");
    }

    public void disableMagiskNotification() {

                if (contains(customCommand("/data/data/com.zalexdev.stryker/files/sqlite3 "
                        + "/data/adb/magisk.db"
                        + " \"UPDATE policies SET logging='0',notification='0' WHERE package_name='"
                        + "com.zalexdev.stryker"
                        + "';\""), "no such"))
                {customCommand("/data/data/com.zalexdev.stryker/files/sqlite3 "
                                        + "/data/adb/magisk.db"
                                        + " \"UPDATE policies SET logging='0',notification='0' WHERE uid='"
                                        + android.os.Process.myUid()
                                        + "';\"");}
    }


    public ArrayList<String> getInterfacesList(){
        ArrayList<String> result = new ArrayList<>();
        ArrayList<String> temp = customChrootCommand("iw dev | grep 'Interface\\|type' | sed -r 's/type//g' | sed -r 's/Interface//g' | sed 'N;s/\\n/,/'",true);

        for (String t : temp){
            String[] l = t.trim().replaceAll("\\s+", " ").split(",");
            result.add(l[0]);
        }
        if (result.contains("managed") || result.contains("monitor") || result.contains("NAN") || result.contains("P2P")){
            result = new ArrayList<>();
            for (String t : temp){
                String[] l = t.trim().replaceAll("\\s+", " ").split(",");
                if (l.length > 1){result.add(l[1]);}

            }
        }

        ArrayList<String> latest = new ArrayList<>();
        for (String s : result){
            if (s.contains("wlan")){
                latest.add(s);
            }
        }
        putListString("interfaces",latest);
        return result;
    }

    public String getHSInterface(){
        return monitorManager.getHSInterface();
    }
    public String getWPSInterface(){
        return getString("wlan_wps");
    }
    public boolean isMonitorModeEnabled(String wlan){
        return monitorManager.isMonitorModeEnabled(wlan);
    }
    public String getDeauthInterface(){
        return monitorManager.getDeauthInterface();
    }

    public Boolean disableMonitorMode(String wlan){
        return monitorManager.disableMonitorMode(wlan);
    }
    public Boolean enableMonitorMode(String wlan){
        return monitorManager.enableMonitorMode(wlan);
    }
    public Boolean enableMonitorMode(String wlan, String channel){
        return monitorManager.enableMonitorMode(wlan,channel);
    }

    public ArrayList<Credentials> getCredentials(){
        ArrayList<String> creds = getListString("creds");
        ArrayList<Credentials> credentialsList = new ArrayList<>();
        for (String cred : creds){
            credentialsList.add(Credentials.fromJson(cred));
        }
        return credentialsList;
    }

    public void addCredentials(Credentials credentials){
        ArrayList<String> credsList = getListString("creds");
        credsList.add(credentials.toJson());
        putListString("creds",credsList);
    }

    public void clearCredentials(){
        putListString("creds",new ArrayList<>());
    }

    public void removeCredentials(int position){
        ArrayList<String> credsList = getListString("creds");
        credsList.remove(position);
        putListString("creds",credsList);
    }

    public void setSmoothLevel(WaveDrawable mWaveDrawable, int level){
        int now = mWaveDrawable.getLevel();
        if (now < level){
            for (int i = now; i < level; i++){

                mWaveDrawable.setLevel(i);
            }
        }else{
            for (int i = now; i > level; i--){
                mWaveDrawable.setLevel(i);
            }
        }
    }




}
