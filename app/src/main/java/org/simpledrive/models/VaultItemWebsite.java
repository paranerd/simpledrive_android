package org.simpledrive.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class VaultItemWebsite extends VaultItem {
    private String url;
    private String user;
    private String pass;

    // Empty element
    public VaultItemWebsite() {
        this("", "", "", "", "", "", "", null, "", null);
    }

    // For all other elements
    public VaultItemWebsite(String title, String category, String type, String url, String user, String pass, String edit, Bitmap icon, String logo, Bitmap logoBmp) {
        super(title, category, type, edit, icon, logo, logoBmp);
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

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

    public String toString() {
        JSONObject job = new JSONObject();
        try {
            job.put("title", getTitle());
            job.put("category", getCategory());
            job.put("type", getType());
            job.put("edit", getEdit());
            job.put("logo", getLogo());
            job.put("url", this.url);
            job.put("user", this.user);
            job.put("pass", this.pass);
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
        super.writeToParcel(parcel, i);
        Log.i("debug", "writeToParcel Website");
        parcel.writeString(this.url);
        parcel.writeString(this.user);
        parcel.writeString(this.pass);
    }

    public static final Parcelable.Creator<VaultItemWebsite> CREATOR = new Parcelable.Creator<VaultItemWebsite>() {
        @Override
        public VaultItemWebsite createFromParcel(Parcel parcel) {
            return new VaultItemWebsite(parcel);
        }

        @Override
        public VaultItemWebsite[] newArray(int size) {
            return new VaultItemWebsite[size];
        }
    };

    public VaultItemWebsite(Parcel in) {
        super(in);
        Log.i("debug", "VaultItemWebsite(Parcel in)");
        this.url = in.readString();
        this.user = in.readString();
        this.pass = in.readString();
    }
}
