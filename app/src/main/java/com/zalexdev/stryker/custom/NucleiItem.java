package com.zalexdev.stryker.custom;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NucleiItem {
    public String title = "";
    public String description = "";
    public ArrayList<String> authors = new ArrayList<>();
    public ArrayList<String> tags = new ArrayList<>();
    public ArrayList<String> references = new ArrayList<>();
    public int severity = 0;
    public String results = "";
    public String cve = "";
    public String host = "";
    public String ip = "";
    public final static int INFO = 0;
    public final static int LOW = 1;
    public final static int MEDIUM = 2;
    public final static int HIGH = 3;
    public final static int CRITICAL = 4;

    public void setAuthors(ArrayList<String> authors) {
        this.authors = authors;
    }

    public void setCve(String cve) {
        this.cve = cve;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setReferences(ArrayList<String> references) {
        this.references = references;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public void setTags(ArrayList<String> tags) {
        this.tags = tags;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSeverity() {
        return severity;
    }

    public ArrayList<String> getAuthors() {
        return authors;
    }

    public ArrayList<String> getReferences() {
        return references;
    }

    public ArrayList<String> getTags() {
        return tags;
    }

    public String getCve() {
        return cve;
    }

    public String getDescription() {
        return description;
    }

    public String getResults() {
        return results;
    }

    public String getTitle() {
        return title;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    @NonNull
    @Override
    public String toString() {
        return "NucleiItem{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", authors=" + authors +
                ", tags=" + tags +
                ", references=" + references +
                ", severity=" + severity +
                ", results='" + results + '\'' +
                ", cve='" + cve + '\'' +
                ", host='" + host + '\'' +
                ", ip='" + ip + '\'' +
                ", INFO=" + INFO +
                ", LOW=" + LOW +
                ", MEDIUM=" + MEDIUM +
                ", HIGH=" + HIGH +
                '}';
    }
    public String getJSON(){
        JSONObject object = new JSONObject();
        JSONObject object2 = new JSONObject();

        try {
            object2.put("matched-at",host);
            object.put("title",title);
            object.put("description",description);
            JSONArray authors = new JSONArray();
            for (String a : getAuthors()){
                authors.put(a);
            }
            object.put("authors",authors);
            JSONArray tags = new JSONArray();
            for (String a : getTags()){
                tags.put(a);
            }
            object.put("tags",tags);
            JSONArray references = new JSONArray();
            for (String a : getReferences()){
                references.put(a);
            }
            object.put("references",references);
            object.put("name",title);
            object.put("severity",severity);
            object2.put("extracted-results",results);
            object.put("cve",cve);
            object2.put("ip",ip);
            object2.put("info",object);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object2.toString();
    }
    public static NucleiItem parseItem(String json){
        NucleiItem nucleiItem = new NucleiItem();
        try {
            JSONObject jsonObject = new JSONObject(json);
            if (jsonObject.has("matched-at")){
                nucleiItem.setHost(jsonObject.getString("matched-at"));
            }
            if (jsonObject.has("extracted-results")){
                nucleiItem.setResults(jsonObject.getString("extracted-results"));
            }
            if (jsonObject.has("ip")){
                nucleiItem.setIp(jsonObject.getString("ip"));
            }
            jsonObject = jsonObject.getJSONObject("info");
            if (jsonObject.has("name")){
                nucleiItem.setTitle(jsonObject.getString("name"));
            }
            if (jsonObject.has("description")){
                nucleiItem.setDescription(jsonObject.getString("description"));
            }
            if (jsonObject.has("severity")){
                String s = jsonObject.getString("severity");
                if (s.contains("info")){
                    nucleiItem.severity = INFO;
                }else if (s.contains("low")){
                    nucleiItem.severity = LOW;
                } else if (s.contains("medium")){
                    nucleiItem.severity = MEDIUM;
                } else if (s.contains("critical")){
                    nucleiItem.severity = CRITICAL;
                } else if (s.contains("high")){
                    nucleiItem.severity = HIGH;
                }else if (s.equals("0")){
                    nucleiItem.severity = INFO;
                }else if (s.equals("1")){
                    nucleiItem.severity = LOW;
                }else if (s.equals("2")){
                    nucleiItem.severity = MEDIUM;
                }else if (s.equals("3")){
                    nucleiItem.severity = HIGH;
                }else if (s.equals("4")){
                    nucleiItem.severity = CRITICAL;
                }else {
                    nucleiItem.severity = INFO;
                }
            }
            String authorsKey = jsonObject.has("authors") ? "authors"
                    : jsonObject.has("author") ? "author" : null;
            if (authorsKey != null && !jsonObject.isNull(authorsKey)) {
                JSONArray array = jsonObject.getJSONArray(authorsKey);
                ArrayList<String> list = new ArrayList<>();
                for (int i = 0; i < array.length();i++){
                    list.add(array.getString(i));
                }
                nucleiItem.setAuthors(list);
            }
            if (jsonObject.has("tags") && !jsonObject.isNull("tags")){
                JSONArray array = jsonObject.getJSONArray("tags");
                ArrayList<String> list = new ArrayList<>();
                for (int i = 0; i < array.length();i++){
                    list.add(array.getString(i));
                }
                nucleiItem.setTags(list);
            }
            String refKey = jsonObject.has("references") ? "references"
                    : jsonObject.has("reference") ? "reference" : null;
            if (refKey != null && !jsonObject.isNull(refKey)) {
                JSONArray array = jsonObject.getJSONArray(refKey);
                ArrayList<String> list = new ArrayList<>();
                for (int i = 0; i < array.length();i++){
                    list.add(array.getString(i));
                }
                nucleiItem.setReferences(list);
            }
            if (jsonObject.has("classification")){
                JSONObject temp = jsonObject.getJSONObject("classification");
                if (temp.has("cve-id") && !temp.isNull("cve-id")){
                    JSONArray array = temp.getJSONArray("cve-id");
                    nucleiItem.setCve(array.getString(0));
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }
        return nucleiItem;
    }
}
