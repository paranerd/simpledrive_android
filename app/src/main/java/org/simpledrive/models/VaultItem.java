package org.simpledrive.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class VaultItem implements Parcelable {
    private String title;
    private String type;
    private String category;
    private String edit;
    private String icon;
    private Bitmap iconBmp;

    public VaultItem(String title, String category, String type, String edit, String icon, Bitmap iconBmp) {
        this.title = title;
        this.category = category;
        this.type = type;
        this.edit = edit;
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

    protected VaultItem(Parcel in) {
        Log.i("debug", "writeToParcel Item");
        this.title = in.readString();
        this.category = in.readString();
        this.type = in.readString();
        this.edit = in.readString();
        this.icon = in.readString();
        this.iconBmp = null;
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
        parcel.writeString(this.edit);
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
}
