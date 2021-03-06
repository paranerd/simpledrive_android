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
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Uploader;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ShareFiles extends AppCompatActivity {
    // General
    private boolean preventLock = false;
    private String accountID = "";

    // Files
    private ArrayList<FileItem> items = new ArrayList<>();
    private ArrayList<FileItem> hierarchy = new ArrayList<>();
    private ArrayList<String> uploadsPending;

    // Interface
    private AbsListView list;
    private TextView info;
    private int listLayout;
    private int theme;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GridView tmp_grid;
    private ListView tmp_list;

    // Request codes
    private final int REQUEST_UNLOCK = 0;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        CustomAuthenticator.setContext(this);
        Connection.init(this);
        Uploader.setContext(this);

        // If there's no account, return to login
        if (CustomAuthenticator.getActiveAccount() == null) {
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }

        uploadsPending = getUploads(getIntent());
        accountID = CustomAuthenticator.getID();

        clearHierarchy();
        initInterface();
        initList();
        initToolbar();
    }

    private void initInterface() {
        // Set theme
        theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
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
                Uploader.addUpload(uploadsPending, hierarchy.get(hierarchy.size() - 1).getID(), "0", null);
                ShareFiles.this.finish();
            }
        });

        fab_folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });

        // Swipe refresh layout
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new FetchFilesFromServer(ShareFiles.this, getCurrentFolderId()).execute();
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(false, Util.dpToPx(56), Util.dpToPx(56) + 100);
    }

    private void initList() {
        listLayout = (Preferences.getInstance(this).read(Preferences.TAG_LIST_LAYOUT, "list").equals("list")) ? R.layout.listview_detail: R.layout.gridview;
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
        if (toolbar != null) {
            toolbar.setPopupTheme(theme);
            setSupportActionBar(toolbar);
        }
    }

    protected void onResume() {
        super.onResume();

        if (CustomAuthenticator.getActiveAccount() == null) {
            // No-one is logged in
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }
        else if (!CustomAuthenticator.isActive(accountID)) {
            // Current account is not active
            finish();
            startActivity(getIntent());
        }
        else if (CustomAuthenticator.isLocked()) {
            requestPIN();
        }
        else {
            preventLock = false;
            new FetchFilesFromServer(this, getCurrentFolderId()).execute();
        }
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

    /**
     * Get the current FolderID from hierarchy
     * If "root"-Folder, ID is 0
     *
     * @return String
     */
    private String getCurrentFolderId() {
        return (hierarchy.size() == 1 && hierarchy.get(hierarchy.size() -1).getFilename().equals("")) ? "0" : hierarchy.get(hierarchy.size() - 1).getID();
    }

    private static class FetchFilesFromServer extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<ShareFiles> ref;
        private String target;

        FetchFilesFromServer(ShareFiles ctx, String target) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final ShareFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... args) {
            Connection con = new Connection("files", "children");
            con.addFormField("target", target);
            con.addFormField("mode", "files");

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final ShareFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (res.successful()) {
                act.extractFiles(res.getMessage());
            }
            else {
                act.showInfo(res.getMessage());
            }
        }
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
                String owner = obj.getString("owner");
                Integer shareStatus = obj.getInt("sharestatus");

                if (type.equals("folder")) {
                    FileItem item = new FileItem(id, filename, "", size, obj.getString("edit"), type, owner, shareStatus);
                    items.add(item);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        displayFiles();
    }

    /**
     * Extract JSONArray from server-data and convert to ArrayList
     */
    private void displayFiles() {
        Util.sortFilesByName(items, 1);

        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        FileAdapter adapter = new FileAdapter(this, listLayout, list, false);
        adapter.setData(items);
        list.setAdapter(adapter);

        // Show current directory in toolbar
        setToolbarTitle(hierarchy.get(hierarchy.size() - 1).getFilename());
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
        new FetchFilesFromServer(this, getCurrentFolderId()).execute();
    }

    private void showInfo(String msg) {
        info.setVisibility(View.VISIBLE);
        info.setText(msg);
    }

    public void onBackPressed() {
        if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new FetchFilesFromServer(this, getCurrentFolderId()).execute();
        }
        else {
            super.onBackPressed();
        }
    }

    private void showCreate() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("New Folder");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                new Create(ShareFiles.this, getCurrentFolderId(), input.getText().toString(), "folder");
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
        Util.showVirtualKeyboard(getApplicationContext());
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

    private static class Create extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<ShareFiles> ref;
        private String target;
        private String filename;
        private String type;

        Create(ShareFiles ctx, String target, String filename, String type) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.filename = filename;
            this.type = type;
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
            if (ref.get() == null) {
                return;
            }

            ShareFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(true);

            if (res.successful()) {
                new FetchFilesFromServer(act, act.getCurrentFolderId());
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}