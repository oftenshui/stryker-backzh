package com.zalexdev.stryker.custom;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

public class Site {
    public String url;
    public String ip;
    public String status;
    public String progress = "0";
    public ArrayList<NucleiItem> nucleis = new ArrayList<>();
    public int[] vulnsCount = new int[]{0,0,0,0,0};
    public String pid = "0";

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setNucleis(ArrayList<NucleiItem> nucleis) {
        this.nucleis = nucleis;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIp() {
        return ip;
    }

    public ArrayList<NucleiItem> getNucleis() {
        return nucleis;
    }

    public String getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }
    public void getNuclei(NucleiItem item){
        nucleis.add(item);
    }
    public String getJSON(){
        JSONObject o = new JSONObject();
        try {
            o.put("url",url);
            o.put("ip",ip);
            o.put("status",status);
            JSONArray array = new JSONArray();
            for (NucleiItem nucleiItem : nucleis){
                array.put(nucleiItem.getJSON());
            }
            o.put("vulns",array);
            o.put("progress",progress);
            JSONArray array2 = new JSONArray();
            for (int i : vulnsCount){
                array2.put(i);
            }
            o.put("pid",pid);
            o.put("vulnsCount",array2);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }
    public static Site parseItem(String json){
        Site site = new Site();
        try {
            JSONObject o = new JSONObject(json);
            site.url = o.getString("url");
            if (o.has("status")) {
                site.status = o.getString("status");}
            else {
                site.status="Scheduled...";
            }
            site.progress = o.getString("progress");
            JSONArray array = o.getJSONArray("vulnsCount");
            for (int i = 0; i < array.length() && i < site.vulnsCount.length; i++){
                site.vulnsCount[i] = array.getInt(i);
            }
            JSONArray array2 = o.getJSONArray("vulns");
            for (int i = 0; i < array2.length();i++){
                site.nucleis.add(NucleiItem.parseItem(array2.getString(i)));
            }
            site.pid = o.getString("pid");
            if (o.has("ip")){
            site.ip =o.getString("ip");}

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return site;
    }
    public void addNuclei(NucleiItem nucleiItem){
        boolean newitem = true;
        for (NucleiItem item : nucleis){
            if (nucleiItem.title.equals(item.title) && nucleiItem.host.equals(item.host)) {
                newitem = false;
                break;
            }
        }
        if (nucleiItem.description.length() <2){
            newitem = false;
        }
        if (newitem){
            int sev = nucleiItem.severity;
            if (sev >= 0 && sev < vulnsCount.length) {
                vulnsCount[sev]++;
            }
            nucleis.add(nucleiItem);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Site{" +
                "url='" + url + '\'' +
                ", ip='" + ip + '\'' +
                ", status='" + status + '\'' +
                ", nucleis=" + nucleis +
                ", vulnsCount=" + Arrays.toString(vulnsCount) +
                '}';
    }
}
