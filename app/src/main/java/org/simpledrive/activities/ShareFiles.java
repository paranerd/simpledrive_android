package org.simpledrive.activities;

import android.app.AlertDialog;
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
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.helper.FileItem;
import org.simpledrive.helper.UploadManager;
import org.simpledrive.helper.Util;

public class ShareFiles extends AppCompatActivity {
    // General
    private ShareFiles e;
    private boolean preventLock = false;
    private boolean calledForUnlock = false;
    private String username = "";
    private int grids = 3;
    private int gridSize;
    private int loginAttempts = 0;

    // Files
    private ArrayList<FileItem> items = new ArrayList<>();
    private ArrayList<FileItem> hierarchy = new ArrayList<>();
    private FileAdapter newAdapter;

    // Interface
    private AbsListView list;
    private TextView info;
    private int listLayout;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GridView tmp_grid;
    private ListView tmp_list;

    private FloatingActionButton fab;
    private FloatingActionButton fab_folder;
    private FloatingActionButton fab_ok;

    private ArrayList<String> uploadsPending;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        SharedPreferences settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

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
                UploadManager.addUpload(e, uploadsPending, hierarchy.get(hierarchy.size() - 1).getJSON().toString(), "0", null);
                e.finish();
            }
        });

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loginAttempts = 0;
                fetchFiles();
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(false, Util.dpToPx(56), Util.dpToPx(56) + 100);

        listLayout = (settings.getString("listlayout", "").length() == 0 || settings.getString("listlayout", "").equals("list")) ? R.layout.filelist : R.layout.filegrid;
        setListLayout();

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
    }

    protected void onResume() {
        super.onResume();

        preventLock = false;

        if (!CustomAuthenticator.enable(this)) {
            // Not logged in
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }
        else if (CustomAuthenticator.isLocked()) {
            if (calledForUnlock) {
                calledForUnlock = false;
                finish();
                return;
            }
            preventLock = true;
            calledForUnlock = true;
            startActivityForResult(new Intent(getApplicationContext(), Unlock.class), 5);
        }
        else {
            connect();
        }

        username = CustomAuthenticator.getUsername();
    }

    protected void onPause() {
        if (!preventLock) {
            calledForUnlock = false;
            CustomAuthenticator.lock();
        }
        super.onPause();
    }

    private void fetchFiles() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSwipeRefreshLayout.setRefreshing(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection con = new Connection("files", "list");
                con.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
                con.addFormField("mode", "files");

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    loginAttempts = 0;
                    extractFiles(res.getMessage());
                    displayFiles();
                }
                else {
                    if (loginAttempts < 2) {
                        connect();
                    }
                }
            }
        }.execute();
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
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));
                String hash = obj.getString("hash");
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((obj.getString("rootshare").length() == 0) ? "" : "shared");
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);

                if(type.equals("folder")) {
                    FileItem item = new FileItem(obj, filename, parent, null, size, obj.getString("edit"), type, owner, hash, icon, null, "", "");
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

        newAdapter = new FileAdapter(this, listLayout, list, gridSize, false, 0);
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

    private void openFile(int position) {
        FileItem item = items.get(position);
        hierarchy.add(item);
        fetchFiles();
    }

    private void connect() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                loginAttempts++;
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("core", "login");
                con.addFormField("user", CustomAuthenticator.getUsername());
                con.addFormField("pass", CustomAuthenticator.getPassword());
                con.forceSetCookie();

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    try {
                        hierarchy = new ArrayList<>();

                        JSONObject currDirJSON = new JSONObject();
                        currDirJSON.put("path", "");
                        currDirJSON.put("rootshare", "");

                        FileItem currDir = new FileItem(currDirJSON, "", "", null);
                        hierarchy.add(currDir);

                        CustomAuthenticator.updateToken(res.getMessage());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    fetchFiles();
                }
                else {
                    // No connection
                    info.setVisibility(View.VISIBLE);
                    info.setText(R.string.connection_error);

                    if (newAdapter != null) {
                        newAdapter.setData(null);
                        newAdapter.notifyDataSetChanged();
                    }

                    mSwipeRefreshLayout.setRefreshing(false);
                    mSwipeRefreshLayout.setEnabled(true);
                }
            }
        }.execute();
    }

    public void onBackPressed() {
        if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            fetchFiles();
        }
        else {
            super.onBackPressed();
        }
    }

    private void showCreate(final String type) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("New " + type.substring(0,1).toUpperCase() + type.substring(1));

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                create(hierarchy.get(hierarchy.size() - 1).getJSON().toString(), input.getText().toString(), type);
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
                InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                if (m != null)
                {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);
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

    private void setListLayout() {
        if (listLayout == R.layout.filelist) {
            list = (ListView) findViewById(R.id.list);
            tmp_grid.setVisibility(View.GONE);
        }
        else {
            list = (GridView) findViewById(R.id.grid);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        registerForContextMenu(list);
    }

    private void create(final String target, final String filename, final String type) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "create");
                con.addFormField("target", target);
                con.addFormField("filename", filename);
                con.addFormField("type", type);

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}