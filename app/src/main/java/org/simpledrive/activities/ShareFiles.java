package org.simpledrive.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
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
import org.simpledrive.R;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.SharedPrefManager;
import org.simpledrive.helper.UploadManager;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.util.ArrayList;

public class ShareFiles extends AppCompatActivity {
    // General
    private ShareFiles ctx;
    private boolean preventLock = false;
    private String username = "";
    private int loginAttempts = 0;
    private boolean waitForTFAUnlock = false;

    // Files
    private ArrayList<FileItem> items = new ArrayList<>();
    private ArrayList<FileItem> hierarchy = new ArrayList<>();
    private FileAdapter newAdapter;

    // Interface
    private AbsListView list;
    private TextView info;
    private int listLayout;
    private int theme;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GridView tmp_grid;
    private ListView tmp_list;

    // Files
    private ArrayList<String> uploadsPending;

    // Request codes
    private final int REQUEST_UNLOCK = 0;
    private final int REQUEST_TFA_CODE = 2;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        ctx = this;
        CustomAuthenticator.setContext(this);
        Connection.init(this);

        // If there's no account, return to login
        if (CustomAuthenticator.getActiveAccount() == null) {
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }

        uploadsPending = getUploads(getIntent());

        clearHierarchy();
        initInterface();
        initList();
        initToolbar();

    }

    private void initInterface() {
        // Set theme
        theme = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set View
        setContentView(R.layout.activity_sharefiles);

        tmp_grid = (GridView) findViewById(R.id.grid);
        tmp_list = (ListView) findViewById(R.id.list);

        info = (TextView) findViewById(R.id.info);

        // Floating Action Buttons
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        FloatingActionButton fab_folder = (FloatingActionButton) findViewById(R.id.fab_folder);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UploadManager.addUpload(ctx, uploadsPending, hierarchy.get(hierarchy.size() - 1).getID(), "0", null);
                ctx.finish();
            }
        });

        fab_folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("folder");
            }
        });

        // Swipe refresh layout
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
    }

    private void initList() {
        listLayout = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_LIST_LAYOUT, "list").equals("list")) ? R.layout.listview_detail: R.layout.gridview;
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
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            toolbar.setPopupTheme(theme);
            setSupportActionBar(toolbar);
        }
    }

    protected void onResume() {
        super.onResume();

        preventLock = false;

        if (CustomAuthenticator.getActiveAccount() == null) {
            // No-one is logged in
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }
        else if (CustomAuthenticator.isLocked()) {
            requestPIN();
        }
        else {
            fetchFiles();
        }

        username = CustomAuthenticator.getUsername();
    }

    protected void onPause() {
        if (!preventLock) {
            CustomAuthenticator.lock();
        }
        super.onPause();
    }

    private void clearHierarchy() {
        if (hierarchy.size() > 0) {
            FileItem first = hierarchy.get(0);
            hierarchy = new ArrayList<>();
            hierarchy.add(first);
        }
        else {
            hierarchy.add(new FileItem("0", "", ""));
        }
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
                Connection con = new Connection("files", "children");
                con.addFormField("target", hierarchy.get(hierarchy.size() - 1).getID());
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

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_UNLOCK:
                if (resultCode == RESULT_OK) {
                    CustomAuthenticator.unlock(data.getStringExtra("passphrase"));
                }
                else {
                    finish();
                }
                break;

            case REQUEST_TFA_CODE:
                if (resultCode == RESULT_OK) {
                    submitTFA(data.getStringExtra("passphrase"));
                }
                else if (CustomAuthenticator.getToken().equals("")) {
                    finish();
                }
                break;
        }
    }

    private void requestPIN() {
        long cooldown = CustomAuthenticator.getCooldown();
        long remainingUnlockAttempts = CustomAuthenticator.getRemainingUnlockAttempts();
        String error = "";

        if (cooldown > 0) {
            error = "Locked for " + cooldown + " second(s).";
        }
        else if (CustomAuthenticator.MAX_UNLOCK_ATTEMPTS - remainingUnlockAttempts > 0 && remainingUnlockAttempts > 0) {
            error = "Incorrect PIN, " + remainingUnlockAttempts + " attempt(s) remaining";
        }
        Intent i = new Intent(getApplicationContext(), PinScreen.class);
        i.putExtra("error", error);
        i.putExtra("length", 4);
        i.putExtra("label", "PIN to unlock");
        startActivityForResult(i, REQUEST_UNLOCK);
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractFiles(String rawJSON) {
        // Reset anything related to listing files
        items = new ArrayList<>();

        try {
            JSONObject job = new JSONObject(rawJSON);
            JSONArray files = new JSONArray(job.getString("files"));
            JSONArray h = new JSONArray(job.getString("hierarchy"));

            // Populate hierarchy
            hierarchy = new ArrayList<>();
            for (int i = 0; i < h.length(); i++) {
                JSONObject obj = h.getJSONObject(i);
                hierarchy.add(new FileItem(obj.getString("id"), obj.getString("filename"), ""));
            }

            for (int i = 0; i < files.length(); i++){
                JSONObject obj = files.getJSONObject(i);

                String id = obj.getString("id");
                String filename = obj.getString("filename");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));
                boolean selfshared = Boolean.parseBoolean(obj.getString("selfshared"));
                boolean shared = Boolean.parseBoolean(obj.getString("shared"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((shared) ? "shared" : "");

                if(type.equals("folder")) {
                    FileItem item = new FileItem(id, filename, "", size, obj.getString("edit"), type, owner, selfshared, shared);
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

        newAdapter = new FileAdapter(this, listLayout, list, false);
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
                CustomAuthenticator.removeToken();
                emptyList();
                mSwipeRefreshLayout.setRefreshing(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("core", "login");
                con.addFormField("user", CustomAuthenticator.getUsername());
                con.addFormField("pass", CustomAuthenticator.getPassword());

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    hierarchy = new ArrayList<>();
                    hierarchy.add(new FileItem("0", "", ""));

                    CustomAuthenticator.setToken(res.getMessage());
                    fetchFiles();
                }
                else {
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                    if (res.getStatus() == 403) {
                        requestTFA(res.getMessage());
                        connect();
                    }
                    else {
                        // No connection
                        showInfo(res.getMessage());

                        mSwipeRefreshLayout.setRefreshing(false);
                        mSwipeRefreshLayout.setEnabled(true);
                    }
                }
            }
        }.execute();
    }

    private void submitTFA(final String code) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showInfo("Evaluating Code...");
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "unlock", 30000);
                con.addFormField("code", code);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    showInfo("");
                }
                else {
                    requestTFA(res.getMessage());
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void requestTFA(String error) {
        Intent i = new Intent(getApplicationContext(), PinScreen.class);
        i.putExtra("error", error);
        i.putExtra("label", "2FA-code");
        i.putExtra("length", 5);
        startActivityForResult(i, REQUEST_TFA_CODE);
        waitForTFAUnlock = true;
    }

    private void emptyList() {
        items = new ArrayList<>();

        if (newAdapter != null) {
            newAdapter.setData(null);
            newAdapter.notifyDataSetChanged();
        }
    }

    private void showInfo(String msg) {
        info.setVisibility(View.VISIBLE);
        info.setText(msg);
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
                create(hierarchy.get(hierarchy.size() - 1).getID(), input.getText().toString(), type);
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
        Util.showVirtualKeyboard(ctx);
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
        if (listLayout == R.layout.listview_detail) {
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
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}