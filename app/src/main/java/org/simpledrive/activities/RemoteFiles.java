package org.simpledrive.activities;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Downloader;
import org.simpledrive.helper.FilelistCache;
import org.simpledrive.helper.Permissions;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Uploader;
import org.simpledrive.helper.Util;
import org.simpledrive.models.AccountItem;
import org.simpledrive.models.FileItem;
import org.simpledrive.services.AudioService;
import org.simpledrive.services.AudioService.LocalBinder;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;

public class RemoteFiles extends AppCompatActivity {
    // General
    private RemoteFiles ctx;
    private String username = "";
    private String viewmode = "files";
    private boolean appVisible;
    private int loginAttempts = 0;
    private int lastSelected = 0;
    private boolean preventLock = false;
    private boolean isAdmin = false;
    private ArrayList<AccountItem> accounts = new ArrayList<>();
    private static boolean waitForTFAUnlock = false;
    private FilelistCache db;
    private long requestId = 0;

    // Request codes
    private final int REQUEST_UPLOAD = 1;
    private final int REQUEST_TFA_CODE = 2;
    private final int REQUEST_UNLOCK = 3;
    private final int REQUEST_FORCE_RELOAD = 5;
    private final int REQUEST_CACHE = 6;

    // Audio
    private AudioService mPlayerService;
    private boolean mBound = false;
    private final Handler mHandler = new Handler();
    private boolean audioUpdateRunning = false;

    // Interface
    private AbsListView list;
    private int listLayout;
    private int theme;
    private Toolbar toolbar;
    private Toolbar toolbarBottom;
    private ActionMenuView amvMenu;
    private Menu bottomContextMenu;
    private TextView info;
    public SwipeRefreshLayout mSwipeRefreshLayout;
    private Menu mToolbarMenu;
    private Menu mContextMenu;
    private TextView audioTitle;
    private RelativeLayout player;
    private SeekBar seek;
    private ImageView bExit;
    private ImageView bPlay;
    private TextView header_user;
    private TextView header_server;
    private TextView header_indicator;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private SearchView searchView = null;
    private boolean accountsVisible = false;
    private ActionMode actionMode;
    private boolean bottomToolbarEnabled = false;

    // Interface - Floating Action Buttons
    private boolean FAB_Status = false;
    private FloatingActionButton fab;
    private FloatingActionButton fab_file;
    private FloatingActionButton fab_folder;
    private FloatingActionButton fab_upload;
    private FloatingActionButton fab_paste;
    private FloatingActionButton fab_paste_cancel;
    private Animation show_fab_upload;
    private Animation hide_fab_upload;
    private Animation show_fab_folder;
    private Animation hide_fab_folder;
    private Animation show_fab_file;
    private Animation hide_fab_file;
    private Animation show_paste_cancel;
    private Animation hide_paste_cancel;

    // Files
    private ArrayList<FileItem> items = new ArrayList<>();
    private static ArrayList<FileItem> filteredItems = new ArrayList<>();
    private ArrayList<FileItem> hierarchy = new ArrayList<>();
    public FileAdapter newAdapter;
    private int sortOrder = 1;
    private JSONArray clipboard = new JSONArray();
    private boolean deleteAfterCopy = false;

    ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			mPlayerService = null;
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			LocalBinder binder = (LocalBinder) service;
			mPlayerService = binder.getService();
			mBound = true;
		}
    };

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        ctx = this;
        Downloader.setContext(this);
        CustomAuthenticator.setContext(this);
        Connection.init(this);

        // If there's no account, return to login
        if (CustomAuthenticator.getActiveAccount() == null) {
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }

        initInterface();
        initToolbar();
        initDrawer();
        initList();
        initAudioPlayer();
        createCache();

        clearHierarchy();
        new GetVersion(RemoteFiles.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        new GetPermissions(RemoteFiles.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        checkForPendingUploads();
        bottomToolbarEnabled = Preferences.getInstance(this).read(Preferences.TAG_BOTTOM_TOOLBAR, false);
        username = CustomAuthenticator.getUsername();
        updateNavigationDrawer();
        db = new FilelistCache(this, Util.md5(username + CustomAuthenticator.getServer()));

        // Update firebase-token if refreshed
        if (!Preferences.getInstance(this).read(Preferences.TAG_FIREBASE_TOKEN_OLD, "").equals("")) {
            new RefreshToken(
                    RemoteFiles.this,
                    Preferences.getInstance(this).read(Preferences.TAG_FIREBASE_TOKEN_OLD, ""),
                    Preferences.getInstance(this).read(Preferences.TAG_FIREBASE_TOKEN, "")
            ).execute();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CACHE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        createCache();
                    }
                    else {
                        Preferences.getInstance(ctx).write(Preferences.TAG_LOAD_THUMB, false);
                        Toast.makeText(ctx, "Could not create cache", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
        }
    }

    protected void onResume() {
        super.onResume();

        if (CustomAuthenticator.getActiveAccount() == null) {
            // No-one is logged in
            startActivity(new Intent(getApplicationContext(), Login.class));
            finish();
        }
        else if (CustomAuthenticator.isLocked()) {
            requestPIN();
        }
        else if (!waitForTFAUnlock) {
            fetchFiles(false);
            preventLock = false;
            appVisible = true;
        }
    }

    /**
     * Request unlock PIN
     */
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

    private void updateNavigationDrawer() {
        // Populate accounts
        Menu menu = mNavigationView.getMenu();
        menu.removeGroup(R.id.navigation_drawer_group_accounts);

        accounts = CustomAuthenticator.getAllAccounts(false);

        for (int i = 0; i < accounts.size(); i++) {
            menu.add(R.id.navigation_drawer_group_accounts, i, 0, accounts.get(i).getDisplayName()).setIcon(R.drawable.ic_account_circle);
        }

        hideAccounts();

        // Show user info
        header_user.setText(username);
        header_server.setText(CustomAuthenticator.getServer());
    }

    private void setViewmode(String vm) {
        viewmode = vm;

        // Highlight current view
        switch (viewmode) {
            case "trash":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_trash);
                break;
            case "shareout":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_shareout);
                break;
            case "sharein":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_sharein);
                break;
            default:
                mNavigationView.setCheckedItem(R.id.navigation_view_item_files);
                break;
        }

        clearHierarchy();
        fetchFiles(false);
    }

    protected void onPause() {
        if (appVisible && !preventLock) {
            CustomAuthenticator.lock();
        }

        super.onPause();
        appVisible = false;
        unselectAll();
    }

    protected void onDestroy() {
        db.close();
        super.onDestroy();

        if (mBound && AudioService.isPlaying()) {
            Intent localIntent = new Intent(this, RemoteFiles.class);
            localIntent.setAction("org.simpledrive.action.startbackground");
            startService(localIntent);
        }
        else if (mBound) {
            getApplicationContext().unbindService(mServiceConnection);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_UPLOAD:
                if (resultCode == RESULT_OK) {
                    ArrayList<String> ul_paths = new ArrayList<>();

                    String[] paths = data.getStringArrayExtra("paths");
                    Collections.addAll(ul_paths, paths);
                    Uploader.addUpload(this, ul_paths, hierarchy.get(hierarchy.size() - 1).getID(), "0", new Uploader.TaskListener() {
                        @Override
                        public void onFinished() {
                            fetchFiles(true);
                        }
                    });
                    Toast.makeText(ctx, "Upload started", Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_FORCE_RELOAD:
                finish();
                startActivity(getIntent());
                break;

            case REQUEST_TFA_CODE:
                if (resultCode == RESULT_OK) {
                    new SubmitTFA(RemoteFiles.this, data.getStringExtra("passphrase")).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                else if (CustomAuthenticator.getToken().equals("")) {
                    finish();
                }
                break;

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

    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        else if (list.getCheckedItemCount() > 0) {
            unselectAll();
        }
        else if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            fetchFiles(false);
        }
        else if (viewmode.equals("trash")) {
            setViewmode("files");
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mToolbarMenu = menu;

        if (listLayout == R.layout.listview_detail) {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_grid);
            menu.findItem(R.id.toggle_view).setTitle("Grid view");
        }
        else {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_list);
            menu.findItem(R.id.toggle_view).setTitle("List view");
        }

        menu.findItem(R.id.emptytrash).setVisible(viewmode.equals("trash") && filteredItems.size() > 0);
        menu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);
        menu.findItem(R.id.cacheimages).setVisible(getAllImages().size() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.remote_files_toolbar, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
        }
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

            SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String newText) {
                    filter(newText);
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    filter(query);
                    return true;
                }
            };
            searchView.setOnQueryTextListener(queryTextListener);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                break;

            case R.id.selectall:
                selectAll();
                break;

            case R.id.emptytrash:
                new android.support.v7.app.AlertDialog.Builder(this)
                        .setTitle("Empty trash")
                        .setMessage("Are you sure you want to delete all files in this folder?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectAll();
                                //delete(getAllSelected(), viewmode.equals("trash"));
                                new Delete(ctx, getAllSelected(), viewmode.equals("trash")).execute();
                                unselectAll();
                            }

                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;

            case R.id.toggle_view:
                listLayout = (listLayout == R.layout.gridview) ? R.layout.listview_detail: R.layout.gridview;
                setListLayout(listLayout);
                initList();
                displayFiles();
                break;

            case R.id.cacheimages:
                cacheAllThumbs();
                cacheAllImages();
                break;
        }

        return true;
    }

    /**
     * Initialize all interface elements
     */
    private void initInterface() {
        // Set theme
        theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set view
        setContentView(R.layout.activity_remotefiles);

        // Bottom toolbar
        toolbarBottom = (Toolbar) findViewById(R.id.toolbar_bottom);
        amvMenu = (ActionMenuView) (toolbarBottom != null ? toolbarBottom.findViewById(R.id.amvMenu) : null);

        // Audioplayer
        bPlay = (ImageView) findViewById(R.id.bPlay);
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.togglePlay();
            }
        });

        bExit = (ImageView) findViewById(R.id.bExit);
        bExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.stop();
            }
        });

        player = (RelativeLayout) findViewById(R.id.audioplayer);
        audioTitle = (TextView) findViewById(R.id.audio_title);

        seek = (SeekBar) findViewById(R.id.seekBar1);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (AudioService.isPlaying() && fromUser) {
                    mPlayerService.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Info
        info = (TextView) findViewById(R.id.info);

        // Floating Action Buttons
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab_upload = (FloatingActionButton) findViewById(R.id.fab_upload);
        fab_file = (FloatingActionButton) findViewById(R.id.fab_file);
        fab_folder = (FloatingActionButton) findViewById(R.id.fab_folder);
        fab_paste = (FloatingActionButton) findViewById(R.id.fab_paste);
        fab_paste_cancel = (FloatingActionButton) findViewById(R.id.fab_paste_cancel);

        show_fab_upload = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_1_show);
        hide_fab_upload = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_1_hide);

        show_fab_folder = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_2_show);
        hide_fab_folder = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_2_hide);

        show_fab_file = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_3_show);
        hide_fab_file = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_3_hide);

        show_paste_cancel = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_diagonal_show);
        hide_paste_cancel = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_diagonal_hide);

        fab_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFAB(false);
                preventLock = true;
                Intent i = new Intent(ctx, FileSelector.class);
                i.putExtra("multi", true);
                i.putExtra("foldersonly", false);
                startActivityForResult(i, REQUEST_UPLOAD);
            }
        });

        fab_folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFAB(false);
                showCreate("folder");
            }
        });

        fab_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFAB(false);
                showCreate("file");
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FAB_Status) {
                    // Display FAB menu
                    toggleFAB(true);
                }
                else {
                    // Close FAB menu
                    toggleFAB(false);
                }
            }
        });

        fab_paste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Paste(RemoteFiles.this, hierarchy.get(hierarchy.size() - 1).getID(), clipboard.toString(), deleteAfterCopy).execute();
            }
        });

        fab_paste_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clipboard = new JSONArray();
                togglePaste(false);
            }
        });

        // Swipe refresh layout
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loginAttempts = 0;
                if (hierarchy.size() > 0 && CustomAuthenticator.isActive(username)) {
                    fetchFiles(true);
                }
                else {
                    new Connect(RemoteFiles.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Util.dpToPx(56), Util.dpToPx(56) + 100);
        mSwipeRefreshLayout.setEnabled(true);
    }

    /**
     * Show/Hide the small Floating Action Buttons
     *
     * @param status true for show, false for hide
     */
    private void toggleFAB(boolean status) {
        if (status == FAB_Status) {
            return;
        }

        FAB_Status = status;

        // Upload FAB
        Animation anim1 = (status) ? show_fab_upload : hide_fab_upload;
        fab_upload.startAnimation(anim1);
        fab_upload.setClickable(status);

        // Folder FAB
        Animation anim2 = (status) ? show_fab_folder : hide_fab_folder;
        fab_folder.startAnimation(anim2);
        fab_folder.setClickable(status);

        // File FAB
        Animation anim3 = (status) ? show_fab_file : hide_fab_file;
        fab_file.startAnimation(anim3);
        fab_file.setClickable(status);
    }

    /**
     * Show/Hide paste and paste-cancel Floating Action Buttons
     *
     * @param show Whether or not to show FAB
     */
    private void togglePaste(boolean show) {
        toggleFAB(false);

        int visibility = (show) ? View.VISIBLE : View.GONE;
        fab_paste.setVisibility(visibility);
        Animation anim = (show) ? show_paste_cancel : hide_paste_cancel;
        fab_paste_cancel.setAnimation(anim);
        fab_paste_cancel.getAnimation().start();
        fab_paste_cancel.setClickable(show);
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

    /**
     * Load files from cache or server
     *
     * @param forceRefresh Load files from server even if cached
     */
    private void fetchFiles(boolean forceRefresh) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (viewmode.equals("files") && (forceRefresh || !fetchFilesFromCache())) {
            new FetchFilesFromServer(RemoteFiles.this, getCurrentFolderId(), viewmode).execute();
        }
    }

    /**
     * Load files from DB-cache
     * @return if successful
     */
    private boolean fetchFilesFromCache() {
        if (newAdapter != null) {
            newAdapter.cancelThumbLoad();
        }

        // Reset anything related to listing files
        toggleFAB(false);
        emptyList();

        // Cache-parent is "0" for root elements
        String parent = getCurrentFolderId();
        ArrayList<FileItem> cachedFiles = db.getChildren(parent);

        // Add files
        for (FileItem item : cachedFiles) {
            items.add(item);
            filteredItems.add(item);
        }

        displayFiles();

        return (cachedFiles.size() > 0);
    }

    /**
     * Fetch files from server
     */
    private static class FetchFilesFromServer extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private final long currentRequestId = Util.getTimestamp();
        private String target;
        private String viewmode;

        FetchFilesFromServer(RemoteFiles ctx, String target, String viewmode) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.viewmode = viewmode;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                if (act.newAdapter != null) {
                    act.newAdapter.cancelThumbLoad();
                }

                act.requestId = currentRequestId;
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... args) {
            Connection con = new Connection("files", "children");
            con.addFormField("target", target);
            con.addFormField("mode", viewmode);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (act.requestId != currentRequestId) {
                return;
            }
            if (res.successful()) {
                act.loginAttempts = 0;
                act.extractFiles(res.getMessage());
                act.displayFiles();
            }
            else {
                act.showInfo(res.getMessage());
                if (act.loginAttempts < 2) {
                    act.clearHierarchy();
                    new Connect(act).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     *
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractFiles(String rawJSON) {
        // Cache-parent is "0" for root elements
        String parent = getCurrentFolderId();

        // Reset anything related to listing files
        toggleFAB(false);
        emptyList();
        db.deleteFolder(parent);

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
                String filename = obj.getString ("filename");
                String type = obj.getString("type");
                String edit = obj.getString("edit");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));
                boolean selfshared = obj.has("selfshared") && Boolean.parseBoolean(obj.getString("selfshared"));
                boolean shared = obj.has("shared") && Boolean.parseBoolean(obj.getString("shared"));
                String owner = obj.getString("owner");

                FileItem item = new FileItem(id, filename, "", size, edit, type, owner, selfshared, shared);
                items.add(item);
                filteredItems.add(item);

                db.addFile(item, parent);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void cacheAllImages() {
        ArrayList<FileItem> images = getAllImages();
        int[] dimensions = Util.getDisplayDimensions(ctx);

        for (FileItem item : images) {
            if (Downloader.isCached(item) == null) {
                Downloader.queueForCache(item, dimensions[0], dimensions[1], false);
            }
        }
    }

    private void cacheAllThumbs() {
        ArrayList<FileItem> images = getAllImages();
        int thumbSize = Util.getThumbSize(ctx, listLayout);

        for (FileItem item : images) {
            if (Downloader.isThumbnailCached(item, 1) == null) {
                Downloader.queueForCache(item, thumbSize, thumbSize, true);
            }
        }
    }

    private int countFolders() {
        int count = 0;
        for (int i = 0; i < filteredItems.size(); i++) {
            if (!filteredItems.get(i).is("folder")) {
                count = i;
                break;
            }
        }
        return count;
    }

    private void displayFiles() {
        Util.sortFilesByName(filteredItems, sortOrder);
        Util.sortFilesByName(items, sortOrder);

        if (filteredItems.size() == 0) {
            showInfo(getString(R.string.empty));
        }
        else {
            info.setVisibility(View.GONE);
        }

        newAdapter = new FileAdapter(this, listLayout, list);
        newAdapter.setData(filteredItems);
        list.setAdapter(newAdapter);

        // Show current directory in toolbar
        String title;
        FileItem thisFolder = hierarchy.get(hierarchy.size() - 1);

        if (thisFolder.getFilename().length() > 0) {
            title = thisFolder.getFilename();
        }
        else if (viewmode.equals("shareout")) {
            title = "My shares";
        }
        else if (viewmode.equals("sharein")) {
            title = "Shared with me";
        }
        else if (viewmode.equals("trash")) {
            title = "Trash";
        }
        else {
            title = "Homefolder";
        }

        int foldersCount = countFolders();
        setToolbarTitle(title);
        setToolbarSubtitle("Folders: " + foldersCount + ", Files: " + (filteredItems.size() - foldersCount));

        unselectAll();
    }

    private void filter(String needle) {
        if (items.size() > 0) {
            filteredItems = new ArrayList<>();

            for (FileItem item : items) {
                if (item.getFilename().toLowerCase().contains(needle)) {
                    filteredItems.add(item);
                }
            }
            displayFiles();
        }
    }

    private void open(int position) {
        FileItem item = filteredItems.get(position);
        if (viewmode.equals("trash")) {
            return;
        }
        else if (item.is("folder")) {
            hierarchy.add(item);
            fetchFiles(false);
        }
        else if (item.is("image")) {
            preventLock = true;
            Intent i = new Intent(getApplicationContext(), ImageViewer.class);
            i.putExtra("position", getImagePosition(item));
            startActivity(i);
        }
        else if (item.is("audio") && mPlayerService != null) {
            mPlayerService.initPlay(item);
            showAudioPlayer();
            Toast.makeText(this, "Loading audio...", Toast.LENGTH_SHORT).show();
        }
        else if (item.is("text")) {
            preventLock = true;
            Intent i = new Intent(getApplicationContext(), Editor.class);
            i.putExtra("file", filteredItems.get(position).getID());
            i.putExtra("filename", filteredItems.get(position).getFilename());
            startActivity(i);
        }
        else if (item.is("pdf")) {
            Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show();

            Downloader.download(item, new Downloader.TaskListener() {
                @Override
                public void onFinished(boolean success, String path) {
                    Log.i("debug", "onFinished: " + path);
                    if (success) {
                        openLocally(path, "application/pdf");
                    }
                }
            });
        }
        else {
            Toast.makeText(this, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
    }

    private void openLocally(String path, String mimeType) {
        File file = new File(path);
        Intent target = new Intent(Intent.ACTION_VIEW);
        target.setDataAndType(Uri.fromFile(file), mimeType);
        target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        Intent intent = Intent.createChooser(target, "Open file");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No application found to open the file", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBottomToolbar() {
        toolbarBottom.setVisibility(View.VISIBLE);
    }

    private void hideBottomToolbar() {
        toolbarBottom.setVisibility(View.GONE);
    }

    private void showAudioPlayer() {
        player.setVisibility(View.VISIBLE);
        audioTitle.setText(AudioService.currentPlaying.getFilename());

        if (!audioUpdateRunning) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    audioUpdateRunning = true;
                    if (AudioService.isPlaying()) {
                        int pos = mPlayerService.getCurrentPosition();

                        seek.setProgress(pos);
                        bPlay.setImageResource(R.drawable.ic_pause);
                    }
                    else {
                        bPlay.setImageResource(R.drawable.ic_play);
                    }

                    if (AudioService.isActive() && appVisible) {
                        mHandler.postDelayed(this, 1000);
                    }
                    else {
                        audioUpdateRunning = false;
                        hideAudioPlayer();
                    }
                }
            });
        }
    }

    private void hideAudioPlayer() {
        player.setVisibility(View.GONE);
    }

    private int getImagePosition(FileItem item) {
        ArrayList<FileItem> allImages = getAllImages();
        for (int i = 0; i < allImages.size(); i++) {
            if (item == allImages.get(i)) {
                return i;
            }
        }
        return 0;
    }

    public static ArrayList<FileItem> getAllImages() {
        ArrayList<FileItem> images = new ArrayList<>();

        for (FileItem item : filteredItems) {
            if (item.is("image")) {
                images.add(item);
            }
        }

        return images;
    }

    private boolean isItemSelected(int pos) {
        SparseBooleanArray checked = list.getCheckedItemPositions();
        return checked.get(pos);
    }

    private void selectAll() {
        for (int i = 0; i < filteredItems.size(); i++) {
            list.setItemChecked(i, true);
        }
    }

    /**
     * Remove selection from all elements
     */
    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    private String getAllSelected() {
        JSONArray arr = new JSONArray();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                arr.put(filteredItems.get(i).getID());
            }
        }

        return arr.toString();
    }

    private String getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return filteredItems.get(i).getID();
            }
        }
        return "";
    }

    private static class Connect extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;

        Connect(RemoteFiles ctx) {
            this.ref = new WeakReference<>(ctx);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.loginAttempts++;
                CustomAuthenticator.removeToken();
                act.emptyList();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... login) {
            Connection con = (waitForTFAUnlock) ? new Connection("core", "login", 30000) : new Connection("core", "login");
            con.addFormField("user", CustomAuthenticator.getUsername());
            con.addFormField("pass", CustomAuthenticator.getPassword());
            con.addFormField("callback", String.valueOf(waitForTFAUnlock));
            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                CustomAuthenticator.setToken(res.getMessage());
                if (waitForTFAUnlock) {
                    act.finishActivity(act.REQUEST_TFA_CODE);
                }
                waitForTFAUnlock = false;
                act.fetchFiles(false);
                new GetVersion(act).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                new GetPermissions(act).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                act.checkForPendingUploads();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
                if (res.getStatus() == 403) {
                    act.requestTFA(res.getMessage());
                    new Connect(act).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                else {
                    // No connection
                    act.showInfo(res.getMessage());

                    act.mSwipeRefreshLayout.setRefreshing(false);
                    act.mSwipeRefreshLayout.setEnabled(true);
                }
            }
        }
    }

    private static class SubmitTFA extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String code;

        SubmitTFA(RemoteFiles ctx, String code) {
            this.ref = new WeakReference<>(ctx);
            this.code = code;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.showInfo("Evaluating Code...");
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "unlock");
            con.addFormField("code", code);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.showInfo("");
            }
            else {
                act.requestTFA(res.getMessage());
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
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
        filteredItems = new ArrayList<>();

        if (newAdapter != null) {
            newAdapter.setData(null);
            newAdapter.notifyDataSetChanged();
        }
    }

    private void showInfo(String msg) {
        info.setVisibility(View.VISIBLE);
        info.setText(msg);
    }

    private void checkForPendingUploads() {
        String photosyncFolder = Preferences.getInstance(this).read(Preferences.TAG_PHOTO_SYNC, "");
        long lastPhotoSync = Preferences.getInstance(this).read(Preferences.TAG_LAST_PHOTO_SYNC, 0L);
        final ArrayList<String> pending = new ArrayList<>();

        if (!photosyncFolder.equals("")) {
            File dir = new File(photosyncFolder);

            File[] elements = dir.listFiles();
            for (File file : elements) {
                if (!file.isDirectory() && file.lastModified() > lastPhotoSync) {
                    pending.add(file.getAbsolutePath());
                }
            }
        }

        if (pending.size() > 0) {
            new android.support.v7.app.AlertDialog.Builder(ctx)
                    .setTitle("Uploads pending")
                    .setMessage("Upload " + pending.size() + (pending.size() == 1 ? " file?" : " files?"))
                    .setPositiveButton("Upload", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Uploader.addUpload(ctx, pending, hierarchy.get(hierarchy.size() - 1).getID(), "1", null);
                        }

                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void unhideDrawerItem(int id) {
        Menu navMenu = mNavigationView.getMenu();
        navMenu.findItem(id).setVisible(true);
    }

    private void showShare(final String target) {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final View shareView = View.inflate(this, R.layout.dialog_share, null);
        final EditText shareUser = (EditText) shareView.findViewById(R.id.shareUser);
        final CheckBox shareWrite = (CheckBox) shareView.findViewById(R.id.shareWrite);
        final CheckBox sharePublic = (CheckBox) shareView.findViewById(R.id.sharePublic);

        dialog.setTitle("Share")
                .setView(shareView)
                .setCancelable(true)
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        final AlertDialog dialog2 = dialog.create();
        dialog2.show();
        dialog2.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (shareUser.getText().toString().isEmpty() && !sharePublic.isChecked()) {
                    Toast.makeText(ctx, "Enter a username", Toast.LENGTH_SHORT).show();
                }
                else {
                    //share(target, username, shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked());
                    new Share(RemoteFiles.this, target, username, shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked()).execute();
                    dialog2.dismiss();
                }
            }
        });

        shareUser.requestFocus();
        Util.showVirtualKeyboard(ctx);
    }

    private void showCreate(final String type) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("New " + type.substring(0,1).toUpperCase() + type.substring(1));

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Create", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //create(hierarchy.get(hierarchy.size() - 1).getID(), input.getText().toString(), type);
                new Create(RemoteFiles.this, hierarchy.get(hierarchy.size() - 1).getID(), input.getText().toString(), type).execute();
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

    private void showEncrypt(final String source) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Encrypt");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Encrypt", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                new Encrypt(RemoteFiles.this, getCurrentFolderId(), source, input.getText().toString()).execute();
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

    private void showDecrypt(final String source) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Decrypt");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Decrypt", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                new Decrypt(RemoteFiles.this, getCurrentFolderId(), source, input.getText().toString()).execute();
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

    private void showRename(final String filename, final String target) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Rename");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);
        String fn_without_ext = (filename.lastIndexOf('.') != -1) ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        input.setText(fn_without_ext);

        alert.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            new Rename(RemoteFiles.this, target, input.getText().toString()).execute();
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

    private void initDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.DrawerLayout);
        mNavigationView = (NavigationView) findViewById(R.id.activity_main_navigation_view);

        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                supportInvalidateOptionsMenu();
                hideAccounts();
            }

            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        header_indicator = (TextView) mNavigationView.getHeaderView(0).findViewById(R.id.navigation_drawer_header_account_indicator);
        header_user = (TextView) mNavigationView.getHeaderView(0).findViewById(R.id.navigation_drawer_header_user);
        header_server = (TextView) mNavigationView.getHeaderView(0).findViewById(R.id.navigation_drawer_header_server);

        mNavigationView
                .getHeaderView(0)
                .findViewById(R.id.navigation_drawer_header_clickable)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleAccounts();
                    }
                });

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                hideAccounts();

                if (item.getGroupId() == R.id.navigation_drawer_group_accounts) {
                    if (Downloader.isRunning() || Uploader.isRunning()) {
                        Toast.makeText(ctx, "Up-/Download running", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        CustomAuthenticator.setActive(accounts.get(item.getItemId()).getName());
                        finish();
                        startActivity(getIntent());
                    }
                }
                else {
                    switch (item.getItemId()) {
                        case R.id.navigation_view_item_files:
                            setViewmode("files");
                            break;

                        case R.id.navigation_view_item_shareout:
                            setViewmode("shareout");
                            break;

                        case R.id.navigation_view_item_sharein:
                            setViewmode("sharein");
                            break;

                        case R.id.navigation_view_item_trash:
                            setViewmode("trash");
                            break;

                        case R.id.navigation_view_item_vault:
                            preventLock = true;
                            startActivity(new Intent(getApplicationContext(), Vault.class));
                            break;

                        case R.id.navigation_view_item_settings:
                            preventLock = true;
                            startActivityForResult(new Intent(getApplicationContext(), AppSettings.class), REQUEST_FORCE_RELOAD);
                            break;

                        case R.id.navigation_view_item_server:
                            preventLock = true;
                            startActivity(new Intent(getApplicationContext(), ServerSettings.class));
                            break;

                        case R.id.navigation_view_item_logout:
                            new android.support.v7.app.AlertDialog.Builder(ctx)
                                    .setTitle("Logout")
                                    .setMessage("Are you sure you want to logout?")
                                    .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            logout();
                                        }

                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            break;

                        case R.id.navigation_view_item_add_account:
                            startActivityForResult(new Intent(getApplicationContext(), Login.class), REQUEST_FORCE_RELOAD);
                            break;

                        case R.id.navigation_view_item_manage_accounts:
                            startActivityForResult(new Intent(getApplicationContext(), Accounts.class), REQUEST_FORCE_RELOAD);
                            break;
                    }
                }
                return true;
            }
            }
        );
    }

    private void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setPopupTheme(theme);
        }
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        amvMenu.setOnMenuItemClickListener(new ActionMenuView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                executeContextAction(menuItem.getItemId());
                return onOptionsItemSelected(menuItem);
            }
        });
        // Inflate a menu to be displayed in the toolbar
        MenuInflater inflater = getMenuInflater();
        bottomContextMenu = amvMenu.getMenu();
        inflater.inflate(R.menu.remote_files_toolbar_bottom, bottomContextMenu);
    }

    private void executeContextAction(int id) {
        switch (id) {
            case R.id.selectall:
                selectAll();
                break;

            case R.id.rename:
                showRename(filteredItems.get(lastSelected).getFilename(), getFirstSelected());
                actionMode.finish();
                break;

            case R.id.delete:
                //delete(getAllSelected(), viewmode.equals("trash"));
                new Delete(this, getAllSelected(), viewmode.equals("trash")).execute();
                actionMode.finish();
                break;

            case R.id.restore:
                new Restore(RemoteFiles.this, getAllSelected()).execute();
                actionMode.finish();
                break;

            case R.id.copy:
                if (deleteAfterCopy) {
                    clipboard = new JSONArray();
                }

                deleteAfterCopy = false;

                for (int i = 0; i < filteredItems.size(); i++) {
                    if (list.isItemChecked(i)) {
                        clipboard.put(filteredItems.get(i).getID());
                    }
                }

                Toast.makeText(this, clipboard.length() + " files to copy", Toast.LENGTH_SHORT).show();
                togglePaste(true);
                actionMode.finish();
                break;

            case R.id.cut:
                if (!deleteAfterCopy) {
                    clipboard = new JSONArray();
                }

                deleteAfterCopy = true;

                for (int i = 0; i < filteredItems.size(); i++) {
                    if (list.isItemChecked(i)) {
                        clipboard.put(filteredItems.get(i).getID());
                    }
                }

                Toast.makeText(this, clipboard.length() + " files to cut", Toast.LENGTH_SHORT).show();
                togglePaste(true);
                actionMode.finish();
                break;

            case R.id.download:
                Downloader.queueForDownload(getAllSelected());
                actionMode.finish();
                break;

            case R.id.zip:
                new Zip(RemoteFiles.this, hierarchy.get(hierarchy.size() - 1).getID(), getAllSelected()).execute();
                actionMode.finish();
                break;

            case R.id.unzip:
                new Unzip(RemoteFiles.this, hierarchy.get(hierarchy.size() - 1).getID(), getFirstSelected()).execute();
                actionMode.finish();
                break;

            case R.id.share:
                showShare(getFirstSelected());
                actionMode.finish();
                break;

            case R.id.unshare:
                new Unshare(RemoteFiles.this, getFirstSelected()).execute();
                actionMode.finish();
                break;

            case R.id.encrypt:
                showEncrypt(getAllSelected());
                actionMode.finish();
                break;

            case R.id.decrypt:
                showDecrypt(getFirstSelected());
                actionMode.finish();
                break;
        }
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

    private void initList() {
        // Set layout
        listLayout = (Preferences.getInstance(this).read(Preferences.TAG_LIST_LAYOUT, "list").equals("list")) ? R.layout.listview_detail: R.layout.gridview;
        setListLayout(listLayout);

        // Set listeners
        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                unselectAll();

                if (bottomToolbarEnabled) {
                    hideBottomToolbar();
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode am, Menu menu) {
                actionMode = am;

                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.remote_files_context, menu);
                mContextMenu = menu;

                if (bottomToolbarEnabled) {
                    showBottomToolbar();
                }

                toggleFAB(false);
                unselectAll();
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                executeContextAction(item.getItemId());
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                boolean trash = viewmode.equals("trash");
                FileItem item = filteredItems.get(position);
                int checkedItemcount = list.getCheckedItemCount();
                mContextMenu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);

                if (bottomToolbarEnabled) {
                    bottomContextMenu.findItem(R.id.restore).setVisible(trash);
                    bottomContextMenu.findItem(R.id.download).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.copy).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.cut).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.zip).setVisible(!trash && !item.is("archive"));
                    bottomContextMenu.findItem(R.id.unzip).setVisible(!trash && item.is("archive"));
                    bottomContextMenu.findItem(R.id.rename).setVisible(!trash && checkedItemcount == 1);
                    bottomContextMenu.findItem(R.id.share).setVisible(!trash && checkedItemcount == 1 && !item.selfshared());
                    bottomContextMenu.findItem(R.id.unshare).setVisible(!trash && checkedItemcount == 1 && item.selfshared());
                    bottomContextMenu.findItem(R.id.encrypt).setVisible(!trash && checkedItemcount == 1 && !item.is("folder") && !item.is("encrypted"));
                    bottomContextMenu.findItem(R.id.decrypt).setVisible(!trash && checkedItemcount == 1 && item.is("encrypted"));
                }
                else {
                    mContextMenu.findItem(R.id.restore).setVisible(trash);
                    mContextMenu.findItem(R.id.download).setVisible(!trash);
                    mContextMenu.findItem(R.id.delete).setVisible(true);
                    mContextMenu.findItem(R.id.copy).setVisible(!trash);
                    mContextMenu.findItem(R.id.cut).setVisible(!trash);
                    mContextMenu.findItem(R.id.zip).setVisible(!trash && !item.is("archive"));
                    mContextMenu.findItem(R.id.unzip).setVisible(!trash && item.is("archive"));
                    mContextMenu.findItem(R.id.rename).setVisible(!trash && checkedItemcount == 1);
                    mContextMenu.findItem(R.id.share).setVisible(!trash && checkedItemcount == 1 && !item.selfshared());
                    mContextMenu.findItem(R.id.unshare).setVisible(!trash && checkedItemcount == 1 && item.selfshared());
                    mContextMenu.findItem(R.id.encrypt).setVisible(!trash && checkedItemcount == 1 && !item.is("folder") && !item.is("encrypted"));
                    mContextMenu.findItem(R.id.decrypt).setVisible(!trash && checkedItemcount == 1 && item.is("encrypted"));
                }

                lastSelected = position;
                newAdapter.notifyDataSetChanged();
                mode.setTitle(list.getCheckedItemCount() + " selected");
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                if (lastSelected > -1) {
                    for (int i = Util.getMin(lastSelected, position); i < Util.getMax(lastSelected, position); i++) {
                        list.setItemChecked(i, isItemSelected(i));
                    }
                }
                lastSelected = position;
                return true;
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                open(position);
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

    private void setListLayout(int layout) {
        listLayout = layout;
        String layoutString = (listLayout == R.layout.gridview) ? "grid" : "list";
        Preferences.getInstance(this).write(Preferences.TAG_LIST_LAYOUT, layoutString);

        if (listLayout == R.layout.listview_detail) {
            list = (ListView) findViewById(R.id.list);
            // Hide Grid
            findViewById(R.id.grid).setVisibility(View.GONE);
        }
        else {
            list = (GridView) findViewById(R.id.grid);
            // Hide List
            findViewById(R.id.list).setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        if (mToolbarMenu != null) {
            invalidateOptionsMenu();
        }
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

    private void createCache() {
        Permissions pm = new Permissions(ctx, REQUEST_CACHE);
        pm.wantStorage();
        pm.request("Access files", "Need access to files to create cache folder.", new Permissions.TaskListener() {
            @Override
            public void onPositive() {
                // Create cache folder
                //if (!Cache.create()) {
                if (!Util.createCache()) {
                    Preferences.getInstance(ctx).write(Preferences.TAG_LOAD_THUMB, false);
                    Toast.makeText(ctx, "Could not create cache", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNegative() {
                Preferences.getInstance(ctx).write(Preferences.TAG_LOAD_THUMB, false);
                Toast.makeText(ctx, "Could not create cache", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initAudioPlayer() {
        // Prepare audio player
        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        if (AudioService.isPlaying()) {
            showAudioPlayer();
        }
        else {
            hideAudioPlayer();
        }
    }

    private static class Delete extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private boolean fullyDelete;

        Delete(RemoteFiles ctx, String target, boolean fullyDelete) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.fullyDelete = fullyDelete;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (ref.get() != null) {
                RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("files", "delete");
            con.addFormField("target", target);
            con.addFormField("final", Boolean.toString(fullyDelete));

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() != null) {
                RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
                if (res.successful()) {
                    act.fetchFiles(true);
                }
                else {
                    Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static class Create extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String filename;
        private String type;

        Create(RemoteFiles ctx, String target, String filename, String type) {
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

            RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(true);

            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Share extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String userfrom;
        private String userto;
        private boolean shareWrite;
        private boolean sharePublic;

        Share(RemoteFiles ctx, String target, String userfrom, String userto, boolean shareWrite, boolean sharePublic) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.userfrom = userfrom;
            this.userto = userto;
            this.shareWrite = shareWrite;
            this.sharePublic = sharePublic;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            String w = (shareWrite) ? "1" : "0";
            String p = (sharePublic) ? "1" : "0";

            Connection con = new Connection("files", "share");
            con.addFormField("target", target);
            con.addFormField("mail", "");
            con.addFormField("key", "");
            con.addFormField("userto", userto);
            con.addFormField("pubAcc", p);
            con.addFormField("write", w);

            return con.finish();
        }
        @Override
        protected void onPostExecute(final Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.fetchFiles(true);

                if (sharePublic) {
                    final android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(act);

                    dialog.setMessage("Send link?").setPositiveButton("Send", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String htmlBody = userfrom + " wants to share a file with you.<br>Access it via the following link:<br><br>" + res.getMessage();
                            Spanned shareBody = Html.fromHtml(htmlBody);
                            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "simpleDrive share link");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                            act.startActivity(Intent.createChooser(sharingIntent, "Send via"));
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Util.copyToClipboard(act, res.getMessage(), "Link copied to clipbard");
                            dialog.cancel();
                        }
                    }).show();
                }
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Unshare extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;

        Unshare(RemoteFiles ctx, String target) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("files", "unshare");
            con.addFormField("target", target);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Zip extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String source;

        Zip(RemoteFiles ctx, String target, String source) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.source = source;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("files", "zip");
            con.addFormField("target", target);
            con.addFormField("source", source);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, "Error zipping", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Unzip extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String source;

        Unzip(RemoteFiles ctx, String target, String source) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.source = source;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("files", "unzip");
            con.addFormField("target", target);
            con.addFormField("source", source);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, "Error unzipping", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Restore extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;

        Restore(RemoteFiles ctx, String target) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("files", "restore");
            con.addFormField("target", target);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Paste extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String source;
        private boolean cut;

        Paste(RemoteFiles ctx, String target, String source, boolean cut) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.source = source;
            this.cut = cut;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            String action = (cut) ? "move" : "copy";
            Connection con = new Connection("files", action);
            con.addFormField("target", target);
            con.addFormField("source", source);
            con.addFormField("trash", "false");

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (res.successful()) {
                act.clipboard = new JSONArray();
                act.togglePaste(false);
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class RefreshToken extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String client_old;
        private String client_new;

        RefreshToken(RemoteFiles ctx, String client_old, String client_new) {
            this.ref = new WeakReference<>(ctx);
            this.client_old = client_old;
            this.client_new = client_new;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "update");
            con.addFormField("client_old", client_old);
            con.addFormField("client_new", client_new);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                Preferences.getInstance(act).write(Preferences.TAG_FIREBASE_TOKEN_OLD, "");
                Toast.makeText(act, "Updated 2FA-Token", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class GetPermissions extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;

        GetPermissions(RemoteFiles ctx) {
            this.ref = new WeakReference<>(ctx);
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection con = new Connection("user", "admin");
            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful() && res.getMessage().equals("true")) {
                act.isAdmin = true;
                act.unhideDrawerItem(R.id.navigation_view_item_server);
            }
        }
    }

    private static class GetVersion extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;

        GetVersion(RemoteFiles ctx) {
            this.ref = new WeakReference<>(ctx);
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection con = new Connection("core", "version");

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            if (res.successful()) {
                try {
                    JSONObject job = new JSONObject(res.getMessage());
                    String latest = job.getString("recent");
                    if (latest.length() > 0 && !latest.equals("null")) {
                        Toast.makeText(act, "Server update available", Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class Rename extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String filename;

        Rename(RemoteFiles ctx, String target, String filename) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.filename = filename;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("files", "rename");
            con.addFormField("target", target);
            con.addFormField("newFilename", filename);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Encrypt extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String source;
        private String secret;

        Encrypt(RemoteFiles ctx, String target, String source, String secret) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.source = source;
            this.secret = secret;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("files", "encrypt");
            con.addFormField("target", target);
            con.addFormField("source", source);
            con.addFormField("secret", secret);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Decrypt extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<RemoteFiles> ref;
        private String target;
        private String source;
        private String secret;

        Decrypt(RemoteFiles ctx, String target, String source, String secret) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.source = source;
            this.secret = secret;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final RemoteFiles act = ref.get();
                act.mSwipeRefreshLayout.setRefreshing(true);
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("files", "decrypt");
            con.addFormField("target", target);
            con.addFormField("source", source);
            con.addFormField("secret", secret);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final RemoteFiles act = ref.get();
            act.mSwipeRefreshLayout.setRefreshing(false);
            if (res.successful()) {
                act.fetchFiles(true);
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideAccounts() {
        accountsVisible = false;
        header_indicator.setText("\u25BC");
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts, false);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts_management, false);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_one, true);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_two, true);
        mNavigationView.getMenu().findItem(R.id.navigation_view_item_server).setVisible(isAdmin);
    }

    private void toggleAccounts() {
        accountsVisible = !accountsVisible;
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts, accountsVisible);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts_management, accountsVisible);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_one, !accountsVisible);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_two, !accountsVisible);

        if (accountsVisible) {
            header_indicator.setText("\u25B2");
        }
        else {
            header_indicator.setText("\u25BC");
            mNavigationView.getMenu().findItem(R.id.navigation_view_item_server).setVisible(isAdmin);
        }
    }

    private void logout() {
        Connection.logout();
        CustomAuthenticator.logout();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                finish();
                if (CustomAuthenticator.getAllAccounts(true).size() == 0) {
                    startActivity(new Intent(getApplicationContext(), Login.class));
                }
                else {
                    startActivity(getIntent());
                }
            }
        }, 100);
    }
}