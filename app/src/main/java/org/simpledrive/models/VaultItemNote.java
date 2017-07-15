package org.simpledrive.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class VaultItemNote extends VaultItem {
    private String content;

    // Empty element
    public VaultItemNote() {
        this("", "", "", "", "", null);
    }

    // For all other elements
    public VaultItemNote(String title, String category, String content, String edit, String icon, Bitmap iconBmp) {
        super(title, category, "note", edit, icon, iconBmp);
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String toString() {
        JSONObject job = new JSONObject();
        try {
            job.put("title", getTitle());
            job.put("category", getCategory());
            job.put("type", getType());
            job.put("edit", getEdit());
            job.put("icon", getIcon());
            job.put("content", this.content);
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
        parcel.writeString(this.content);
    }

    public static final Parcelable.Creator<VaultItemNote> CREATOR = new Parcelable.Creator<VaultItemNote>() {
        @Override
        public VaultItemNote createFromParcel(Parcel parcel) {
            return new VaultItemNote(parcel);
        }

        @Override
        public VaultItemNote[] newArray(int size) {
            return new VaultItemNote[size];
        }
    };

    public VaultItemNote(Parcel in) {
        super(in);
        this.content= in.readString();
    }
}
