package com.zalexdev.stryker.custom;

public class License {
    public String name;
    public String desc;
    public String url;
    public String copyright;
    public boolean header;

    public License() {
    }

    public static License of(String name, String desc, String copyright, String url) {
        License l = new License();
        l.name = name;
        l.desc = desc;
        l.copyright = copyright;
        l.url = url;
        return l;
    }

    public static License section(String title) {
        License l = new License();
        l.name = title;
        l.header = true;
        return l;
    }
}
