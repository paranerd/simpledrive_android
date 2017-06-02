package org.simpledrive.models;

public class AccountItem {
    private String server;
    private String displayName;

    public AccountItem(String server, String nick) {
        this.server = server;
        this.displayName = nick;
    }

    public String getServer() {
        return this.server;
    }

    public String getDisplayName() {
        return (this.displayName != null && !this.displayName.equals("") && !this.displayName.equals(this.server)) ? this.displayName : this.server;
    }
}
