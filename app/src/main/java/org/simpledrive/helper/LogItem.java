package org.simpledrive.helper;

import android.graphics.Bitmap;

public class LogItem {
    private String message;
    private String user;
    private String date;
    private String type;
    private Bitmap icon;

    // For all other elements
    public LogItem(String message, String user, String date, String type, Bitmap icon) {
        this.message = message;
        this.user = user;
        this.date = date;
        this.type = type;
        this.icon = icon;
    }

    public Bitmap getIcon() {
        return this.icon;
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
