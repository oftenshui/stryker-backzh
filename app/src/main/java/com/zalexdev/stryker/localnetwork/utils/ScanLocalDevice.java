package com.zalexdev.stryker.localnetwork.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.Port;
import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jcifs.netbios.NbtAddress;

public class ScanLocalDevice extends AsyncTask<Void, String, Device> {
    public String exec = Core.EXECUTE;
    public String ip;
    public Core core;

    public ScanLocalDevice(String i, Context context) {
        ip = i;
        try{
        core = new Core(context);}
        catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @SuppressLint("WrongThread")
    @Override
    protected Device doInBackground(Void... command) {
        String line;
        Device d = new Device();
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            if (core.getBoolean("fast_scan")) {
                stdin.write((exec + "'nmap " + ip + " -F --top 100 -n -Pn -O  --max-os-tries 1'&&echo LOCALSCANFINISHED" + '\n').getBytes());
            } else {
                stdin.write((exec + "'nmap " + ip + " -n -Pn -O --max-os-tries 1 '&&echo LOCALSCANFINISHED" + '\n').getBytes());
            }
            stdin.flush();
            stdin.close();
            ArrayList<String> nmapoutput = new ArrayList<>();
            ArrayList<String> outerror = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                nmapoutput.add(line);
                if (line.contains("LOCALSCANFINISHED")) {
                    d = localdevices(nmapoutput);

                }
            }
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                outerror.add(line);
            }
            if (core != null){
            
            }
            br.close();
            process.waitFor();
            process.destroy();

        } catch (IOException | InterruptedException e) {
        }
        try {
            NbtAddress[] nbts = NbtAddress.getAllByAddress(ip);
            String netbiosname = nbts[nbts.length-1].getHostName();
            d.setSubname(netbiosname);
        } catch (Exception ignored) {
        }
        return d;
    }

    @Override
    protected void onPostExecute(Device result) {
        super.onPostExecute(result);

    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);

    }

    public Device localdevices(ArrayList<String> output) throws IOException {
        Device device = new Device();ArrayList<Port> ports = new ArrayList<>();device.setIp(ip);
        device.setShim(false);
        for (int i = 0; i < output.size(); i++) {
            String temp = output.get(i).replaceAll("\\s+", " ").replace("*", "");


            String portnum;
            String service;
            String banner;
            if (temp.contains("/tcp")) {
                Port port = new Port();
                String r = temp.replace("/tcp", "").replace("open", "").replace("filtered", "").replaceAll("\\\\x([A-Z]|[0-9])([A-Z]|[0-9])","");
                Matcher m = Pattern.compile("[0-9]+").matcher(r);
                if (m.find()) {
                    portnum = m.group();
                    service = r.replaceAll("\\s+", "").replace(portnum, "");
                    port.setPortNumber(portnum);
                    port.setPortName(service);
                    ports.add(port);
                }
            } else if (temp.contains("banner")) {
                StringBuilder sb = new StringBuilder();
                banner = temp.replace("banner:","").replace("|", "").replace("_","").replaceAll("\\\\x([A-Z]|[0-9])([A-Z]|[0-9])","");
                sb.append(banner).append("\n");
                String banner2 = "";
                String banner3 = "";
                try {
                if (output.get(i+1).contains("|")) {
                    banner2 = output.get(i+1).replace("|", "").replace("_","").replaceAll("\\\\x([A-Z]|[0-9])([A-Z]|[0-9])","");
                }
                if (output.get(i+2).contains("|")) {
                    banner3 = output.get(i+2).replace("|", "").replace("_","").replaceAll("\\\\x([A-Z]|[0-9])([A-Z]|[0-9])","");
                }}catch (Exception ignored){}
                sb.append(banner2).append("\n");
                sb.append(banner3).append("\n");
                ports.get(ports.size()-1).setBanner(banner);
            }else if (temp.contains("MAC Address")) {
                Matcher mac = Pattern.compile("((\\w{2}:){5}\\w{2})").matcher(temp);
                if (mac.find()) {
                    device.setMac(Objects.requireNonNull(mac.group(0)).toUpperCase(Locale.ROOT));
                }
                String vendor = temp.replace("MAC Address: ", "").replace(mac + " ", "").replace("(", "").replace(")", "").replace(mac.group() + " ", "");
                device.setVendor(vendor);
            }else if (temp.contains("Running:")){
                device.setOs(temp.replace("Running: ","").replace("Microsoft",""));
            }else if (temp.contains("No exact matches")){
                device.setOs("Unknown");
            }

        } device.setPorts(ports);
        device.setNmapoutput(output);
        return device;
    }

}
