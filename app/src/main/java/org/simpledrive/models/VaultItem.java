package org.simpledrive.models;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class VaultItem implements Parcelable {
    private String id;
    private String title;
    private String logoName;
    private Drawable logo;
    private String group;
    private String edit;
    private String url;
    private String username;
    private String password;
    private String note;

    public VaultItem() {
        this("", "", "General", "", "", "", "", "", "");
    }

    public VaultItem(String id, String title, String group, String edit, String logoName, String url, String username, String password, String note) {
        this.id = id;
        this.title = title;
        this.group = group;
        this.edit = edit;
        this.logoName = logoName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.note = note;
    }

    public String getId() { return this.id; }

    public void setId(String id) { this.id = id; }

    public String getLogoName() { return this.logoName; }

    public void setLogoName(String logoName) { this.logoName = logoName; }

    public Drawable getLogo() {
        return this.logo;
    }

    public void setLogo(Drawable logo) { this.logo = logo; }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) { this.title = title; }

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) { this.group = group; }

    public void setEdit(String edit) {
        this.edit = edit;
    }

    public String getEdit() {
        return this.edit;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return this.username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return this.password;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getNote() {
        return this.note;
    }

    public String toString() {
        JSONObject job = new JSONObject();
        try {
            job.put("id", getId());
            job.put("title", getTitle());
            job.put("group", getGroup());
            job.put("edit", getEdit());
            job.put("logo", getLogoName());
            job.put("url", this.url);
            job.put("username", this.username);
            job.put("password", this.password);
            job.put("note", this.note);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return job.toString();
    }

    VaultItem(Parcel in) {
        this.id = in.readString();
        this.title = in.readString();
        this.group = in.readString();
        this.edit = in.readString();
        this.logoName = in.readString();
        this.url = in.readString();
        this.username = in.readString();
        this.password = in.readString();
        this.note = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.id);
        parcel.writeString(this.title);
        parcel.writeString(this.group);
        parcel.writeString(this.edit);
        parcel.writeString(this.logoName);
        parcel.writeString(this.url);
        parcel.writeString(this.username);
        parcel.writeString(this.password);
        parcel.writeString(this.note);
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
}
