package org.simpledrive.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class VaultItem implements Parcelable {
    private String title;
    private String category;
    private String type;
    private String url;
    private String edit;
    private String user;
    private String pass;
    private String note;
    private String icon;
    private Bitmap iconBmp;

    // Empty element
    public VaultItem() {
        this("", "", "", "", "", "", "", "", "", null);
    }

    // For all other elements
    public VaultItem(String title, String category, String type, String url, String user, String pass, String edit, String note, String icon, Bitmap iconBmp) {
        this.title = title;
        this.category = category;
        this.type = type;
        this.url = url;
        this.user = user;
        this.pass = pass;
        this.edit = edit;
        this.note = note;
        this.icon = icon;
        this.iconBmp = iconBmp;
    }

    public Bitmap getIconBmp() {
        return this.iconBmp;
    }

    public void setIconBmp(Bitmap icon) {
        this.iconBmp = icon;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) { this.title = title; }

    public String getURL() {
        return this.url;
    }

    public void setURL(String url) { this.url = url; }

    public String getUser() {
        return this.user;
    }

    public void setUser(String user) { this.user = user; }

    public String getPass() {
        return this.pass;
    }

    public void setPass(String pass) { this.pass = pass; }

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) { this.category = category; }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEdit() {
        return this.edit;
    }

    public String getNote() {
        return this.note;
    }

    public String toString() {
        JSONObject job = new JSONObject();
        try {
            job.put("title", this.title);
            job.put("category", this.category);
            job.put("type", this.type);
            job.put("url", this.url);
            job.put("user", this.user);
            job.put("pass", this.pass);
            job.put("edit", this.edit);
            job.put("note", this.note);
            job.put("icon", this.icon);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return job.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.title);
        parcel.writeString(this.category);
        parcel.writeString(this.type);
        parcel.writeString(this.url);
        parcel.writeString(this.user);
        parcel.writeString(this.pass);
        parcel.writeString(this.edit);
        parcel.writeString(this.note);
        parcel.writeString(this.icon);
    }

    public static final Parcelable.Creator<VaultItem> CREATOR = new Parcelable.Creator<VaultItem>() {
        @Override
        public VaultItem createFromParcel(Parcel parcel) {
            return new VaultItem(parcel);
        }

        @Override
        public VaultItem[] newArray(int size) {
            return new VaultItem[size];
        }
    };

    public VaultItem(Parcel in) {
        this.title = in.readString();
        this.category = in.readString();
        this.type = in.readString();
        this.url = in.readString();
        this.user = in.readString();
        this.pass = in.readString();
        this.edit = in.readString();
        this.note = in.readString();
        this.icon = in.readString();
        this.iconBmp = null;
    }
}
