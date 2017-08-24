package org.simpledrive.models;

import android.graphics.drawable.Drawable;

public class LogItem {
    private String message;
    private String user;
    private String date;
    private String type;
    private Drawable icon;

    // For all other elements
    public LogItem(String message, String user, String date, String type) {
        this.message = message;
        this.user = user;
        this.date = date;
        this.type = type;
    }

    public Drawable getIcon() {
        return this.icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public String getMessage() {
        return this.message;
    }

    public String getUser() {
        return this.user;
    }

    public String getDate() {
        return this.date;
    }

    public String getType() {
        return this.type;
    }
}
