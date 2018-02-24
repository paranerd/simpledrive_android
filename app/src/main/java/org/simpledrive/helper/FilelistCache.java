package org.simpledrive.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.simpledrive.models.FileItem;

import java.util.ArrayList;

public class FilelistCache extends SQLiteOpenHelper {
    // Database Version
    private static final int DATABASE_VERSION = 5;

    // Database Name
    private static final String DATABASE_NAME = "cache";

    // Table name
    private static final String TABLE_FILES = "files";

    // Table Columns names
    private static final String KEY_ID = "id";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_FILENAME = "filename";
    private static final String KEY_PARENT = "parent";
    private static final String KEY_SIZE = "size";
    private static final String KEY_EDIT = "edit";
    private static final String KEY_TYPE = "type";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_SHARESTATUS = "sharestatus";

    // Account hash
    private static String account;

    public FilelistCache(Context context, String acc) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        account = acc;
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_FILES_TABLE = "CREATE TABLE " + TABLE_FILES + "("
                + KEY_ID + " VARCHAR(32) PRIMARY KEY,"
                + KEY_ACCOUNT + " VARCHAR(32),"
                + KEY_FILENAME + " VARCHAR(100),"
                + KEY_PARENT + " VARCHAR(32),"
                + KEY_SIZE + " INT(11),"
                + KEY_EDIT + " INT(11),"
                + KEY_TYPE + " VARCHAR(10),"
                + KEY_OWNER + " INT(11),"
                + KEY_SHARESTATUS + " INT(11))";
        db.execSQL(CREATE_FILES_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILES);

        // Create tables again
        onCreate(db);
    }

    /**
     * All CRUD(Create, Read, Update, Delete) Operations
     */
    // Adding new contact
    public void addFile(FileItem file, String parent, boolean replace) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_ID, file.getID());
        values.put(KEY_ACCOUNT, account);
        values.put(KEY_FILENAME, file.getFilename());
        values.put(KEY_PARENT, parent);
        values.put(KEY_SIZE, file.getSize());
        values.put(KEY_EDIT, file.getEdit());
        values.put(KEY_TYPE, file.getType());
        values.put(KEY_OWNER, file.getOwner());
        values.put(KEY_SHARESTATUS, file.getShareStatus());

        // Inserting Row
        //db.insert(TABLE_FILES, null, values);
        int mode = (replace) ? SQLiteDatabase.CONFLICT_REPLACE : SQLiteDatabase.CONFLICT_IGNORE;
        db.insertWithOnConflict(TABLE_FILES, null, values, mode);
        db.close();
    }

    // Getting single file
    FileItem getFile(String id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_FILES,
                new String[] { KEY_ID, KEY_FILENAME, KEY_SIZE },
                KEY_ID + "=?",
                new String[] { id },
                null, null, null, null
        );

        if (cursor != null) {
            cursor.moveToFirst();
            FileItem file = new FileItem(cursor.getString(0), cursor.getString(1), "", cursor.getString(2), "", "", "", 0);
            cursor.close();
            return file;
        }

        return null;
    }

    // Getting all files for parent-id
    public ArrayList<FileItem> getChildren(String id) {
        ArrayList<FileItem> files = new ArrayList<>();
        // Select All Query
        String selectQuery = "SELECT * FROM " + TABLE_FILES + " WHERE " + KEY_ACCOUNT + " = ? AND " + KEY_PARENT + " = ?";
        String[] selectArgs = { account, id };

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, selectArgs);

        // Looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                files.add(new FileItem(cursor.getString(0), cursor.getString(2), "", cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getInt(8)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return files;
    }

    private FileItem getParent(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "SELECT " + KEY_ID + "," + KEY_FILENAME
                + " FROM " + TABLE_FILES
                + " WHERE " + KEY_ID + " = (SELECT " + KEY_PARENT + " FROM " + TABLE_FILES + " WHERE " + KEY_ID + " = ?)";
        String[] args = { id };

        Cursor cursor = db.rawQuery(query, args);

        if (cursor != null && cursor.moveToFirst()) {
            FileItem parent = new FileItem(cursor.getString(0), cursor.getString(1), "");
            cursor.close();
            return parent;
        }

        return null;
    }

    public ArrayList<FileItem> getHierarchy(String id) {
        ArrayList<FileItem> hierarchy = new ArrayList<>();
        FileItem parent;

        do {
            parent = getParent(id);

            if (parent != null) {
                hierarchy.add(parent);
                id = parent.getID();
            }
        } while (parent != null);

        return hierarchy;
    }

    // Updating single file
    /*public int updateFile(FileItem file) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_NAME, contact.getName());
        values.put(KEY_PH_NO, contact.getPhoneNumber());

        // Update row
        return db.update(TABLE_CONTACTS, values, KEY_ID + " = ?",
                new String[] { String.valueOf(contact.getID()) });
    }*/

    // Deleting single contact
    public void deleteFolder(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FILES, KEY_ACCOUNT + " = ? AND " + KEY_PARENT + " = ?",
                new String[] { String.valueOf(account), String.valueOf(id) });
    }

    public void clear() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FILES, KEY_ACCOUNT + " = ?", new String[] { account });
    }
}