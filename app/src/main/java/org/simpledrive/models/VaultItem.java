package org.simpledrive.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class VaultItem implements Parcelable {
    private String title;
    private String type;
    private String category;
    private String edit;
    private Bitmap icon;
    private String logo;
    private Bitmap logoBmp;

    VaultItem(String title, String category, String type, String edit, String logo) {
        this.title = title;
        this.category = category;
        this.type = type;
        this.edit = edit;
        this.logo = logo;
    }

    public Bitmap getIcon() {
        return this.icon;
    }

    public void setIcon(Bitmap icon) {
        this.icon= icon;
    }

    public String getLogo() {
        return this.logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public Bitmap getLogoBmp() {
        return this.logoBmp;
    }

    public void setLogoBmp(Bitmap logoBmp) {
        this.logoBmp = logoBmp;
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

    public void setEdit(String edit) {
        this.edit = edit;
    }

    public String getEdit() {
        return this.edit;
    }

    VaultItem(Parcel in) {
        this.title = in.readString();
        this.category = in.readString();
        this.type = in.readString();
        this.edit = in.readString();
        this.logo = in.readString();
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
        parcel.writeString(this.logo);
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
