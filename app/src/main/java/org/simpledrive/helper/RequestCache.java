package org.simpledrive.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RequestCache extends SQLiteOpenHelper {
    // Database version
    private static final int DATABASE_VERSION = 5;

    // Database name
    private static final String DATABASE_NAME = "cache";

    // Table name
    private static final String TABLE = "requests";

    // Table columns
    private static final String KEY_ID = "id";
    private static final String KEY_MODE = "mode";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_REQUEST = "request";

    // Account hash
    private static String account;

    public RequestCache(Context context, String acc) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        account = acc;
    }

    // Create Table
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_FILES_TABLE = "CREATE TABLE " + TABLE + "("
                + KEY_ID + " VARCHAR(32),"
                + KEY_MODE + " VARCHAR(10),"
                + KEY_ACCOUNT + " VARCHAR(32),"
                + KEY_REQUEST + " TEXT,"
                + "PRIMARY KEY (" + KEY_ID + "," + KEY_MODE + "))";
        db.execSQL(CREATE_FILES_TABLE);
    }

    // Upgrade database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);

        // Create tables again
        onCreate(db);
    }

    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */
    // Add new request
    public void add(String id, String mode, String request) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_ID, id);
        values.put(KEY_MODE, mode);
        values.put(KEY_ACCOUNT, account);
        values.put(KEY_REQUEST, request);

        // Inserting Row
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    // Get request
    public String get(String id, String mode) {
        // Select All Query
        String selectQuery =
                "SELECT " + KEY_REQUEST
                + " FROM " + TABLE
                + " WHERE " + KEY_ACCOUNT + " = ?"
                + " AND " + KEY_ID + " = ?"
                + " AND " + KEY_MODE + " = ?";
        String[] selectArgs = { account, id, mode };

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, selectArgs);

        if (cursor != null && cursor.moveToFirst()) {
            String request = cursor.getString(0);
            cursor.close();
            return request;
        }

        return null;
    }

    // Delete request
    public void delete(String id, String mode) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE, KEY_ACCOUNT + " = ? AND " + KEY_ID + " = ? AND " + KEY_MODE + " = ?",
                new String[] { account, id, mode });
    }

    public void clear() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE, KEY_ACCOUNT + " = ?", new String[] { account });
    }
}