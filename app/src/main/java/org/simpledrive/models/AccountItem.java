package org.simpledrive.models;

public class AccountItem
{
    private String name;
    private String server;
    private String user;
    private String alias;

    public AccountItem(String name, String server, String user, String alias)
    {
        this.name = name;
        this.server = server;
        this.user = user;
        this.alias = alias;
    }

    public String getName()
    {
        return this.name;
    }

    public String getServer()
    {
        return this.server;
    }

    public String getUser()
    {
        return this.user;
    }

    public String getDisplayName()
    {
        return (this.alias != null && !this.alias.equals("")) ? this.alias : this.name;
    }
}
