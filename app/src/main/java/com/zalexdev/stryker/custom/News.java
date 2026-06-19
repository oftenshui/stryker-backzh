package com.zalexdev.stryker.custom;

import android.graphics.Bitmap;

public class News {
    public String title = "No title";
    public String description = "No description";
    public boolean actionbutton1 = false;
    public boolean actionbutton2 = false;
    public String imageUrl = "";
    public String actionbutton1text = "Open";
    public String actionbutton2text = "";
    public String actionbutton1url = "";
    public String actionbutton2url = "";
    public String newsUrl = "";
    public String newsDate = "";
    public Bitmap image = null;
    public boolean pinned = false;
    public int id = 0;

    public boolean hasImage() {
        return image != null;
    }

    public boolean hasArticleLink() {
        return newsUrl != null && !newsUrl.isEmpty();
    }
}
