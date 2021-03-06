package org.simpledrive.helper;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    // Preference name
    private static final String SHARED_PREF_NAME = "org.simpledrive.shared_pref";

    // Tags
    public static final String TAG_FIREBASE_TOKEN = "firebasetoken";
    public static final String TAG_FIREBASE_TOKEN_OLD = "firebasetokenold";
    public static final String TAG_LOAD_THUMB = "loadthumb";
    public static final String TAG_BOTTOM_TOOLBAR = "bottomtoolbar";
    public static final String TAG_COLOR_THEME = "colortheme";
    public static final String TAG_LIST_LAYOUT = "listlayout";
    public static final String TAG_PHOTO_SYNC = "photosync";
    public static final String TAG_LAST_PHOTO_SYNC = "lastPhotoSync";
    public static final String TAG_VAULT_IN_SYNC = "vaultinsync";
    public static final String TAG_FINGERPRINT = "fingerprint";

    // General
    private static Preferences instance;
    private static Context ctx;

    private Preferences(Context context) {
        ctx = context;
    }

    public static synchronized Preferences getInstance(Context context) {
        if (instance == null) {
            instance = new Preferences(context);
        }
        return instance;
    }

    public String read(String tag, String def) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return  sharedPreferences.getString(tag, def);
    }

    public boolean read(String tag, boolean def) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(tag, def);
    }

    public long read(String tag, long def) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getLong(tag, def);
    }

    public int read(String tag, int def) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(tag, def);
    }

    public boolean write(String tag, String msg) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(tag, msg);
        editor.apply();
        return true;
    }

    public boolean write(String tag, boolean value) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(tag, value);
        editor.apply();
        return true;
    }

    public boolean write(String tag, long value) {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(tag, value);
        editor.apply();
        return true;
    }
}
