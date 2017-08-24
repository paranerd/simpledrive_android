package org.simpledrive.models;

import android.graphics.drawable.Drawable;

public class UserItem {
    private String username;
    private String mode;
    private Drawable icon;

    // For all other elements
    public UserItem(String username, String mode) {
        this.username = username;
        this.mode = mode;
    }

    public Drawable getIcon() {
        return this.icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public String getUsername() {
        return this.username;
    }

    public String getMode() {
        return this.mode;
    }
}
