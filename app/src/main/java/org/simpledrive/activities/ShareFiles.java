package org.simpledrive.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.simpledrive.R;
import org.simpledrive.helper.Connection;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.helper.FileItem;
import org.simpledrive.helper.Util;

public class ShareFiles extends AppCompatActivity {
    // General
    private static int loginAttemts = 0;
    public static ShareFiles e;
    private static String username = "";
    private SharedPreferences settings;
    private static int grids = 3;
    private static int gridSize;

    // Files
    private static ArrayList<FileItem> items = new ArrayList<>();
    private static ArrayList<FileItem> hierarchy = new ArrayList<>();

    // Interface
    private static AbsListView list;
    private TextView info;
    private static String globLayout;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GridView tmp_grid;
    private ListView tmp_list;

    private FloatingActionButton fab;
    private FloatingActionButton fab_folder;
    private FloatingActionButton fab_ok;

    // Upload
    private ArrayList<HashMap<String, String>> uploadQueue = new ArrayList<>();
    public static int uploadCurrent = 0;
    public static int uploadTotal = 0;
    public static int uploadSuccessful = 0;
    public static boolean uploading = false;
    private static ArrayList<String> uploadsPending;


    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        setContentView(R.layout.activity_sharefiles);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        gridSize = displaymetrics.widthPixels / grids;

        tmp_grid = (GridView) findViewById(R.id.grid);
        tmp_list = (ListView) findViewById(R.id.list);

        info = (TextView) findViewById(R.id.info);

        uploadsPending = getUploads(getIntent());

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab_ok = (FloatingActionButton) findViewById(R.id.fab_ok);
        fab_folder = (FloatingActionButton) findViewById(R.id.fab_folder);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fab_folder.getVisibility() == View.GONE) {
                    fab_folder.setVisibility(View.VISIBLE);
                } else {
                    fab_folder.setVisibility(View.GONE);
                }
            }
        });

        fab_folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("folder");
            }
        });

        fab_ok.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                upload_handler(uploadsPending);
                e.finish();
            }
        });

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new ListContent().execute();
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(false, Util.dpToPx(56), Util.dpToPx(56) + 100);

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        globLayout = (settings.getString("view", "").length() == 0) ? "list" : settings.getString("view", "");
        setView(globLayout);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openFile(position);
            }
        });

        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                boolean enable = ((list != null && list.getChildCount() == 0) || (list != null && list.getChildCount() > 0 && list.getFirstVisiblePosition() == 0 && list.getChildAt(0).getTop() == 0));
                mSwipeRefreshLayout.setEnabled(enable);
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
        }

        new Connect().execute();
    }

    private class ListContent extends AsyncTask<String, String, HashMap<String, String>> {
        ProgressDialog pDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(ShareFiles.this);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("files", "list", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            multipart.addFormField("mode", "files");

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            pDialog.dismiss();
            mSwipeRefreshLayout.setRefreshing(false);
            if(value.get("status").equals("ok")) {
                Log.i("status", "ok");
                loginAttemts = 0;
                extractFiles(value.get("msg"));
                displayFiles();
            }
            else {
                Log.i("status", "not ok");
                new Connect().execute();
            }
        }
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractFiles(String rawJSON) {
        // Reset anything related to listing files
        items = new ArrayList<>();

        try {
            JSONArray jar = new JSONArray(rawJSON);

            for(int i = 0; i < jar.length(); i++){
                JSONObject obj = jar.getJSONObject(i);

                String filename = obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? "" : Util.convertSize(obj.getString("size"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((obj.getString("rootshare").length() == 0) ? "" : "shared");
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);

                if(type.equals("folder")) {
                    FileItem item = new FileItem(obj, filename, parent, null, size, obj.getString("edit"), type, owner, icon, null, "", "");
                    items.add(item);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Extract JSONArray from server-data and convert to ArrayList
     */
    private void displayFiles() {
        Util.sortFilesByName(items, 1);

        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        } else {
            info.setVisibility(View.GONE);
        }

        int layout = (globLayout.equals("list")) ? R.layout.filelist : R.layout.filegrid;
        FileAdapter newAdapter = new FileAdapter(e, layout, list, gridSize, false, 0);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);

        // Show current directory in toolbar
        String title;
        FileItem thisFolder = hierarchy.get(hierarchy.size() - 1);
        if (!thisFolder.getFilename().equals("")) {
            title = thisFolder.getFilename();
        } else {
            title = "Homefolder";
        }

        setToolbarTitle(title);
        setToolbarSubtitle("Folders: " + items.size());
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void setToolbarSubtitle(final String subtitle) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    public void openFile(int position) {
        FileItem item = items.get(position);
        hierarchy.add(item);
        new ListContent().execute();
    }

    public class Connect extends AsyncTask<String, String, HashMap<String, String>> {
        Account[] sc;
        AccountManager accMan = AccountManager.get(ShareFiles.this);

        @Override
        protected void onPreExecute() {
            loginAttemts++;
            super.onPreExecute();
        }

        @Override
        protected HashMap<String, String> doInBackground(String... login) {
            accMan = AccountManager.get(ShareFiles.this);
            sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttemts > 2) {
                HashMap<String, String> map = new HashMap<>();
                map.put("status", "error");
                return map;
            }

            username = sc[0].name;

            Connection.setServer(accMan.getUserData(sc[0], "server"));

            Connection multipart = new Connection("core", "login", null);
            multipart.addFormField("user", username);
            multipart.addFormField("pass", accMan.getPassword(sc[0]));
            multipart.forceSetCookie();

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok")) {
                try {
                    hierarchy = new ArrayList<>();

                    JSONObject currDirJSON = new JSONObject();
                    currDirJSON.put("path", "");
                    currDirJSON.put("rootshare", "");

                    FileItem currDir = new FileItem(currDirJSON, "", "");
                    hierarchy.add(currDir);

                    info.setVisibility(View.VISIBLE);
                    info.setText(R.string.empty);
                    Connection.setToken(value.get("msg"));
                    accMan.setUserData(sc[0], "token", value.get("msg"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                new ListContent().execute();
            } else {
                loginAttemts = 0;
                info.setVisibility(View.VISIBLE);
                info.setText(R.string.reconnect_error);
            }
        }
    }

    public void onBackPressed() {
        if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new ListContent().execute();
        }
        else {
            super.onBackPressed();
        }
    }

    private class NewFile extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... pos) {
            Connection multipart = new Connection("files", "create", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            multipart.addFormField("filename", pos[0]);
            multipart.addFormField("type", pos[1]);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok")) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showCreate(final String type) {
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("New " + type.substring(0,1).toUpperCase() + type.substring(1));

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newFilename = input.getText().toString();
                new NewFile().execute(newFilename, type);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
        input.requestFocus();
        input.selectAll();
        showVirtualKeyboard();
    }

    private void showVirtualKeyboard() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                InputMethodManager m = (InputMethodManager) e.getSystemService(Context.INPUT_METHOD_SERVICE);

                if (m != null)
                {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);
    }

    private class Upload extends AsyncTask<String, Integer, HashMap<String, String>> {
        private NotificationCompat.Builder mBuilder;
        private NotificationManager mNotifyManager;
        private int notificationId = 1;
        private String filename;
        private int fullCurrent;
        private int fullTotal;
        private int fullSuccessful;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Intent intent = new Intent(e, RemoteFiles.class);
            PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

            uploadCurrent++;
            fullCurrent = RemoteFiles.uploadCurrent + uploadCurrent;
            fullTotal = RemoteFiles.uploadTotal + uploadTotal;

            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(e);
            mBuilder.setContentIntent(pIntent)
                    .setContentTitle("Uploading " + fullCurrent + " of " + fullTotal)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_cloud)
                    .setProgress(100, 0, false);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected HashMap<String, String> doInBackground(String... path) {
            HashMap<String, String> ul_elem = uploadQueue.remove(0);
            filename = ul_elem.get("filename");
            String filepath = ul_elem.get("path");
            String relative = ul_elem.get("relative");
            String target = ul_elem.get("target");

            Connection multipart = new Connection("files", "upload", new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    if(num % 5 == 0) {
                        publishProgress(num);
                    }
                }
            });

            multipart.addFormField("paths", relative);
            multipart.addFormField("target", target);
            multipart.addFilePart("0", new File(filepath));

            return multipart.finish();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false)
                    .setContentTitle("Uploading " + fullCurrent + " of " + fullTotal)
                    .setContentText(filename);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            uploadSuccessful = (value == null || !value.get("status").equals("ok")) ? uploadSuccessful : uploadSuccessful + 1;
            fullSuccessful = RemoteFiles.uploadSuccessful + uploadSuccessful;
            if(uploadQueue.size() > 0) {
                new Upload().execute();
            }
            else {
                if(!RemoteFiles.uploading) {
                    String file = (fullTotal == 1) ? "file" : "files";
                    mBuilder.setContentTitle("Upload complete")
                            .setContentText(fullSuccessful + " of " + fullTotal + " " + file + " added")
                            .setOngoing(false)
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(notificationId, mBuilder.build());
                    uploading = false;
                }
            }
        }
    }

    public void upload_add_recursive(String orig_path, File dir, String target) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                upload_add_recursive(orig_path, file, target);
            } else {
                String rel_dir = file.getParent().substring(orig_path.length()) + "/";
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", rel_dir);
                ul_elem.put("path", file.getPath());
                ul_elem.put("target", target);
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }
    }

    public void upload_handler(ArrayList<String> paths) {
        for(String path : paths) {
            File file = new File(path);
            if (file.isDirectory()) {
                upload_add_recursive(file.getParent(), file, hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            }
            else {
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", "");
                ul_elem.put("path", path);
                ul_elem.put("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }

        if(!uploading) {
            new Upload().execute();
        }
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) {
            result = contentURI.getPath();
        }
        else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private ArrayList<String> getUploads(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();
        ArrayList<String> uploads = new ArrayList<>();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
               return null;
            }
            if (type.startsWith("image/")) {
                uploads.add(getRealPathFromURI(uri));
            }
            else {
                uploads.add(uri.getPath());
            }
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            for(Uri uri : uris) {
                if (uri.toString().startsWith("content")) {
                    uploads.add(getRealPathFromURI(uri));
                }
                else {
                    uploads.add(uri.getPath());
                }
            }
        }
        else {
            return null;
        }
        return uploads;
    }

    public void setView(String view) {
        globLayout = view;
        settings.edit().putString("view", globLayout).apply();

        if(globLayout.equals("list")) {
            list = (ListView) findViewById(R.id.list);
            tmp_grid.setVisibility(View.GONE);
        } else {
            list = (GridView) findViewById(R.id.grid);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        registerForContextMenu(list);
    }

    protected void onDestroy()
    {
        super.onDestroy();
    }
}