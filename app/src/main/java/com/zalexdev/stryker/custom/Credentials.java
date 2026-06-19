package com.zalexdev.stryker.custom;

import org.json.JSONException;
import org.json.JSONObject;

public class Credentials {
    public String data = "No data";
    public String ip = "No ip";


    public Credentials(String data, String ip) {
        this.data = data;
        this.ip = ip;
    }
    public String getData() {
        return data;
    }

    public String getIp() {
        return ip;
    }



    public void setData(String data) {
        this.data = data;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }



    public String toJson() {
        return "{\"data\":\"" + data + "\",\"ip\":\"" + ip + "\"}";
    }

    public String toString() {
        return "Data: " + data + " | IP: " + ip;
    }

    public static Credentials fromJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            return new Credentials(jsonObject.getString("data"), jsonObject.getString("ip"));
        } catch (JSONException ignored) {
            return new Credentials("No data", "No ip");
        }
    }
}
