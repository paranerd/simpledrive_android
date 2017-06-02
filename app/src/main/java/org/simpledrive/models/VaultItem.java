package org.simpledrive.models;

import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

public class VaultItem {
    private String title;
    private String category;
    private String type;
    private String edit;
    private String user;
    private String pass;
    private String note;
    private String icon;
    private Bitmap iconBmp;

    // For all other elements
    public VaultItem(String title, String category, String type, String edit, String note, String icon, Bitmap iconBmp) {
        this.title = title;
        this.category = category;
        this.type = type;
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

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) { this.category = category; }

    public String getType() {
        return this.type;
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
            job.put("edit", this.edit);
            job.put("note", this.note);
            job.put("icon", this.icon);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return job.toString();
    }
}
