package org.simpledrive.helper;

import android.graphics.Bitmap;

public class UserItem {
    private String username;
    private String mode;
    private Bitmap icon;

    // For all other elements
    public UserItem(String username, String mode, Bitmap icon) {
        this.username = username;
        this.mode = mode;
        this.icon = icon;
    }

    public Bitmap getIcon() {
        return this.icon;
    }

    public String getUsername() {
        return this.username;
    }

    public String getMode() {
        return this.mode;
    }
}
