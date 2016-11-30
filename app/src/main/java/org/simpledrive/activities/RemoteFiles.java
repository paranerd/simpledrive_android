package org.simpledrive.activities;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.AudioService;
import org.simpledrive.helper.AudioService.LocalBinder;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.DownloadManager;
import org.simpledrive.helper.FileItem;
import org.simpledrive.helper.PermissionManager;
import org.simpledrive.helper.UploadManager;
import org.simpledrive.helper.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteFiles extends AppCompatActivity {
    // General
    private boolean appVisible;
    private RemoteFiles e;
    private String username = "";
    private String viewmode = "files";
    private final String CACHE_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private int FORCE_RELOAD = 5;
    private SharedPreferences settings;
    private int loginAttempts = 0;
    private boolean loadthumbs = false;
    private int lastSelected = 0;
    private int grids = 3;
    private int gridSize;
    private boolean forceFullLoad = true;
    private boolean preventLock = false;
    private boolean calledForUnlock = false;
    private boolean isAdmin = false;
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
    private SwipeRefreshLayout mSwipeRefreshLayout;
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
    private GridView tmp_grid;
    private ListView tmp_list;
    private FloatingActionButton fab;
    private FloatingActionButton fab_file;
    private FloatingActionButton fab_folder;
    private FloatingActionButton fab_upload;
    private FloatingActionButton fab_paste;
    private FloatingActionButton fab_paste_cancel;
    private SearchView searchView = null;
    private boolean accountsVisible = false;
    private ActionMode actionMode;
    private boolean bottomToolbarEnabled = false;

    // Files
    private ArrayList<FileItem> items = new ArrayList<>();
    private static ArrayList<FileItem> filteredItems = new ArrayList<>();
    private ArrayList<FileItem> hierarchy = new ArrayList<>();
    private FileAdapter newAdapter;
    private int sortOrder = 1;
    private JSONArray clipboard = new JSONArray();
    private boolean deleteAfterCopy = false;

    ServiceConnection mServiceConnection = new ServiceConnection(){
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

        e = this;
        forceFullLoad = true;

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        listLayout = (settings.getString("listlayout", "list").equals("list")) ? R.layout.filelist : R.layout.filegrid;
        setContentView(R.layout.activity_remotefiles);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        gridSize = displaymetrics.widthPixels / grids;

        initInterface();
        setListLayout(listLayout);
        initToolbar();
        initDrawer();
        initList();
        initAudioPlayer();
        createCache();
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
                        loadthumbs = false;
                        Toast.makeText(e, "Could not create cache", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
        }
    }

    protected void onResume() {
        super.onResume();

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
            startActivityForResult(new Intent(getApplicationContext(), Unlock.class), 4);
        }
        else {
            if (forceFullLoad || CustomAuthenticator.activeAccountChanged) {
                forceFullLoad = false;
                connect();
            }

            calledForUnlock = false;
            username = CustomAuthenticator.getUsername();
            preventLock = false;
            appVisible = true;
            loadthumbs = settings.getBoolean("loadthumb", false);
            bottomToolbarEnabled = settings.getBoolean("bottomtoolbar", false);
            updateNavigationDrawer();
        }
    }

    private void updateNavigationDrawer() {
        // Populate accounts
        Menu menu = mNavigationView.getMenu();
        ArrayList<String> servers = CustomAuthenticator.getAllAccounts(false);
        menu.removeGroup(R.id.navigation_drawer_group_accounts);

        for (String server : servers) {
            menu.add(R.id.navigation_drawer_group_accounts, 1, 0, server).setIcon(R.drawable.ic_account_circle);
        }

        hideAccounts();

        // Show user info
        header_user.setText(username);
        header_server.setText(CustomAuthenticator.getServer());
    }

    private void changeViewmode(String vm) {
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
        fetchFiles();
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
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                ArrayList<String> ul_paths = new ArrayList<>();

                String[] paths = data.getStringArrayExtra("paths");
                Collections.addAll(ul_paths, paths);
                UploadManager.addUpload(this, ul_paths, hierarchy.get(hierarchy.size() - 1).getID(), "0", new UploadManager.TaskListener() {
                    @Override
                    public void onFinished() {
                        fetchFiles();
                    }
                });
                Toast.makeText(e, "Upload started", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == FORCE_RELOAD) {
            finish();
            startActivity(getIntent());
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
            fetchFiles();
        }
        else if (viewmode.equals("trash")) {
            changeViewmode("files");
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mToolbarMenu = menu;

        if (listLayout == R.layout.filelist) {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_grid);
            menu.findItem(R.id.toggle_view).setTitle("Grid view");
        } else {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_list);
            menu.findItem(R.id.toggle_view).setTitle("List view");
        }

        menu.findItem(R.id.emptytrash).setVisible(viewmode.equals("trash") && filteredItems.size() > 0);
        menu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);
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
                                delete(getAllSelected(), viewmode.equals("trash"));
                                unselectAll();
                            }

                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;

            case R.id.toggle_view:
                listLayout = (listLayout == R.layout.filegrid) ? R.layout.filelist : R.layout.filegrid;
                setListLayout(listLayout);
                initList();
                displayFiles();
                break;
        }

        return true;
    }

    private void initInterface() {
        toolbarBottom = (Toolbar) findViewById(R.id.toolbar_bottom);
        amvMenu = (ActionMenuView) (toolbarBottom != null ? toolbarBottom.findViewById(R.id.amvMenu) : null);

        tmp_grid = (GridView) findViewById(R.id.grid);
        tmp_list = (ListView) findViewById(R.id.list);

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

        info = (TextView) findViewById(R.id.info);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab_upload = (FloatingActionButton) findViewById(R.id.fab_upload);
        fab_file = (FloatingActionButton) findViewById(R.id.fab_file);
        fab_folder = (FloatingActionButton) findViewById(R.id.fab_folder);
        fab_paste = (FloatingActionButton) findViewById(R.id.fab_paste);
        fab_paste_cancel = (FloatingActionButton) findViewById(R.id.fab_paste_cancel);

        fab_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab_folder.setVisibility(View.GONE);
                fab_file.setVisibility(View.GONE);
                fab_upload.setVisibility(View.GONE);
                preventLock = true;
                Intent i = new Intent(e, FileSelector.class);
                i.putExtra("multi", true);
                i.putExtra("foldersonly", false);
                startActivityForResult(i, 1);
            }
        });

        fab_folder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("folder");
            }
        });

        fab_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("file");
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fab_folder.getVisibility() == View.GONE) {
                    fab_folder.setVisibility(View.VISIBLE);
                    fab_file.setVisibility(View.VISIBLE);
                    fab_upload.setVisibility(View.VISIBLE);
                } else {
                    fab_folder.setVisibility(View.GONE);
                    fab_file.setVisibility(View.GONE);
                    fab_upload.setVisibility(View.GONE);
                }
            }
        });

        fab_paste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paste(clipboard.toString(), hierarchy.get(hierarchy.size() - 1).getID(), deleteAfterCopy);
            }
        });

        fab_paste_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clipboard = new JSONArray();
                hidePaste();
            }
        });

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loginAttempts = 0;
                if (hierarchy.size() > 0 && CustomAuthenticator.isActive(username)) {
                    fetchFiles();
                }
                else {
                    connect();
                }
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Util.dpToPx(56), Util.dpToPx(56) + 100);
        mSwipeRefreshLayout.setEnabled(true);
    }

    private void showPaste() {
        fab.setVisibility(View.GONE);
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);

        fab_paste.setVisibility(View.VISIBLE);
        fab_paste_cancel.setVisibility(View.VISIBLE);
    }

    private void hidePaste() {
        fab.setVisibility(View.VISIBLE);

        fab_paste.setVisibility(View.GONE);
        fab_paste_cancel.setVisibility(View.GONE);
    }

    private void fetchFiles() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (newAdapter != null) {
                    newAdapter.cancelThumbLoad();
                }

                mSwipeRefreshLayout.setRefreshing(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection con = new Connection("files", "children");
                con.addFormField("target", hierarchy.get(hierarchy.size() - 1).getID());
                con.addFormField("mode", viewmode);

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
                    String msg = res.getMessage();
                    info.setVisibility(View.VISIBLE);
                    info.setText(msg);

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
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);
        fab_upload.setVisibility(View.GONE);

        int thumbSize = (listLayout == R.layout.filelist) ? Util.dpToPx(40) : gridSize;
        items = new ArrayList<>();
        filteredItems = new ArrayList<>();

        try {
            JSONObject job = new JSONObject(rawJSON);
            JSONArray files = new JSONArray(job.getString("files"));
            JSONArray h = new JSONArray(job.getString("hierarchy"));

            // Populate hierarchy
            hierarchy = new ArrayList<>();
            for (int i = 0; i < h.length(); i++) {
                JSONObject obj = h.getJSONObject(i);
                hierarchy.add(new FileItem(obj.getString("id"), obj.getString("filename"), "", null));
            }

            for (int i = 0; i < files.length(); i++){
                JSONObject obj = files.getJSONObject(i);

                String id = obj.getString("id");
                String filename = obj.getString ("filename");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));
                boolean selfshared = Boolean.parseBoolean(obj.getString("selfshared"));
                boolean shared = Boolean.parseBoolean(obj.getString("shared"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((shared) ? "shared" : "");
                Bitmap icon;

                switch (type) {
                    case "folder":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);
                        break;
                    case "audio":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_audio);
                        break;
                    case "pdf":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pdf);
                        break;
                    case "image":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_image);
                        break;
                    case "archive":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_archive);
                        break;
                    default:
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unknown);
                        break;
                }

                Bitmap thumb = null;
                String thumbPath = "";
                String imgPath = "";

                if (type.equals("image")) {
                    String ext = "." + FilenameUtils.getExtension(filename);
                    imgPath = CACHE_FOLDER + Util.md5(id) + ext;
                    String thumbType = (listLayout == R.layout.filelist) ? "list" : "grid";
                    thumbPath = CACHE_FOLDER + Util.md5(id) + "_" + thumbType + ".jpg";


                    if (new File(imgPath).exists()) {
                        thumb = Util.getThumb(imgPath, thumbSize);
                    }
                    else if (new File(thumbPath).exists()) {
                        thumb = Util.getThumb(thumbPath, thumbSize);
                    }
                }


                FileItem item = new FileItem(id, filename, "", null, size, obj.getString("edit"), type, owner, selfshared, shared, icon, thumb, thumbPath, imgPath);
                items.add(item);
                filteredItems.add(item);
            }
        } catch (JSONException exp) {

            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayFiles() {
        Util.sortFilesByName(filteredItems, sortOrder);
        Util.sortFilesByName(items, sortOrder);

        int firstFilePos = filteredItems.size();

        for(int i = 0; i < filteredItems.size(); i++) {
            if (!filteredItems.get(i).is("folder")) {
                firstFilePos = i;
                break;
            }
        }

        if (filteredItems.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        newAdapter = new FileAdapter(this, listLayout, list, gridSize, loadthumbs, firstFilePos);
        newAdapter.setData(filteredItems);
        list.setAdapter(newAdapter);

        // Show current directory in toolbar
        String title;
        FileItem thisFolder = hierarchy.get(hierarchy.size() - 1);
        if (!thisFolder.getID().equals("0")) {
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

        setToolbarTitle(title);
        setToolbarSubtitle("Folders: " + firstFilePos + ", Files: " + (filteredItems.size() - firstFilePos));

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

    private void openFile(int position) {
        FileItem item = filteredItems.get(position);
        if (viewmode.equals("trash")) {
            return;
        }
        else if (item.is("folder")) {
            hierarchy.add(item);
            fetchFiles();
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
        else {
            Toast.makeText(this, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
    }

    private void toggleFAB(boolean hide) {
        int visible = (hide) ? View.GONE : View.VISIBLE;
        fab.setVisibility(visible);
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);
        fab_upload.setVisibility(View.GONE);
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
     * Removes selection from all elements
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

    private void connect() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                loginAttempts++;
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... login) {
                Connection con = new Connection("core", "login");
                con.addFormField("user", CustomAuthenticator.getUsername());
                con.addFormField("pass", CustomAuthenticator.getPassword());
                con.forceSetCookie();
                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    hierarchy = new ArrayList<>();

                    FileItem currDir = new FileItem("0", "", "", null);
                    hierarchy.add(currDir);

                    CustomAuthenticator.updateToken(res.getMessage());
                    fetchFiles();
                    getVersion();
                    getPermissions();
                    checkForPendingUploads();
                }
                else {
                    // No connection
                    info.setVisibility(View.VISIBLE);
                    info.setText(res.getMessage());

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

    private void checkForPendingUploads() {
        String photosyncFolder = settings.getString("photosync", "");
        long lastPhotoSync = settings.getLong("lastPhotoSync", 0);
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
            new android.support.v7.app.AlertDialog.Builder(e)
                    .setTitle("Uploads pending")
                    .setMessage("Upload " + pending.size() + (pending.size() == 1 ? " file?" : " files?"))
                    .setPositiveButton("Upload", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            UploadManager.addUpload(e, pending, hierarchy.get(hierarchy.size() - 1).getID(), "1", null);
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
                    Toast.makeText(e, "Enter a username", Toast.LENGTH_SHORT).show();
                } else {
                    share(target, username, shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked());
                    dialog2.dismiss();
                }
            }
        });

        shareUser.requestFocus();
        showVirtualKeyboard();
    }

    private void showCreate(final String type) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("New " + type.substring(0,1).toUpperCase() + type.substring(1));

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Create", new DialogInterface.OnClickListener() {
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
        showVirtualKeyboard();
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
            rename(target, input.getText().toString());
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
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                if (m != null) {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
  	    }, 100);
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
            public boolean onNavigationItemSelected(final MenuItem item) {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                hideAccounts();

                switch (item.getItemId()) {
                    case R.id.navigation_view_item_files:
                        changeViewmode("files");
                        break;

                    case R.id.navigation_view_item_shareout:
                        changeViewmode("shareout");
                        break;

                    case R.id.navigation_view_item_sharein:
                        changeViewmode("sharein");
                        break;

                    case R.id.navigation_view_item_trash:
                        changeViewmode("trash");
                        break;

                    case R.id.navigation_view_item_settings:
                        preventLock = true;
                        startActivityForResult(new Intent(getApplicationContext(), AppSettings.class), FORCE_RELOAD);
                        break;

                    case R.id.navigation_view_item_server:
                        preventLock = true;
                        startActivity(new Intent(getApplicationContext(), ServerSettings.class));
                        break;

                    case R.id.navigation_view_item_logout:
                        new android.support.v7.app.AlertDialog.Builder(e)
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
                        startActivityForResult(new Intent(getApplicationContext(), Login.class), FORCE_RELOAD);
                        break;

                    case R.id.navigation_view_item_manage_accounts:
                        startActivityForResult(new Intent(getApplicationContext(), Accounts.class), FORCE_RELOAD);
                        break;

                    case 1:
                        if (DownloadManager.isRunning() || UploadManager.isRunning()) {
                            Toast.makeText(e, "Up-/Download running", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            CustomAuthenticator.setActive(item.getTitle().toString());
                            finish();
                            startActivity(getIntent());
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
                delete(getAllSelected(), viewmode.equals("trash"));
                actionMode.finish();
                break;

            case R.id.restore:
                restore(getAllSelected());
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
                showPaste();
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
                showPaste();
                actionMode.finish();
                break;

            case R.id.download:
                DownloadManager.addDownload(this, getAllSelected());
                actionMode.finish();
                break;

            case R.id.zip:
                zip(hierarchy.get(hierarchy.size() - 1).getID(), getAllSelected());
                actionMode.finish();
                break;

            case R.id.share:
                showShare(getFirstSelected());
                actionMode.finish();
                break;

            case R.id.unshare:
                unshare(getFirstSelected());
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
        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                toggleFAB(false);
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

                toggleFAB(true);
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
                mContextMenu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);

                if (bottomToolbarEnabled) {
                    bottomContextMenu.findItem(R.id.restore).setVisible(trash);
                    bottomContextMenu.findItem(R.id.download).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.copy).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.cut).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.zip).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.rename).setVisible(!trash && list.getCheckedItemCount() == 1);
                    bottomContextMenu.findItem(R.id.share).setVisible(!trash && list.getCheckedItemCount() == 1 && !filteredItems.get(position).selfshared());
                    bottomContextMenu.findItem(R.id.unshare).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).selfshared());
                }
                else {
                    mContextMenu.findItem(R.id.restore).setVisible(trash);
                    mContextMenu.findItem(R.id.download).setVisible(!trash);
                    mContextMenu.findItem(R.id.delete).setVisible(true);
                    mContextMenu.findItem(R.id.copy).setVisible(!trash);
                    mContextMenu.findItem(R.id.cut).setVisible(!trash);
                    mContextMenu.findItem(R.id.zip).setVisible(!trash);
                    mContextMenu.findItem(R.id.rename).setVisible(!trash && list.getCheckedItemCount() == 1);
                    mContextMenu.findItem(R.id.share).setVisible(!trash && list.getCheckedItemCount() == 1 && !filteredItems.get(position).selfshared());
                    mContextMenu.findItem(R.id.unshare).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).selfshared());
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

    private void setListLayout(int layout) {
        listLayout = layout;
        String layoutString = (listLayout == R.layout.filegrid) ? "grid" : "list";
        settings.edit().putString("listlayout", layoutString).apply();

        if (listLayout == R.layout.filelist) {
            list = (ListView) findViewById(R.id.list);
            tmp_grid.setVisibility(View.GONE);
        }
        else {
            list = (GridView) findViewById(R.id.grid);
            tmp_list.setVisibility(View.GONE);
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
    }

    private void createCache() {
        PermissionManager pm = new PermissionManager(e, REQUEST_CACHE);
        pm.wantStorage();
        pm.request("Access files", "Need access to files to create cache folder.", new PermissionManager.TaskListener() {
            @Override
            public void onPositive() {
                // Create image cache folder
                File cache = new File(CACHE_FOLDER);
                if (!cache.exists() && !cache.mkdir()) {
                    loadthumbs = false;
                    Toast.makeText(e, "Could not create cache", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNegative() {
                loadthumbs = false;
                Toast.makeText(e, "Could not create cache", Toast.LENGTH_SHORT).show();
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

    private void share(final String target, final String userfrom, final String userto, final boolean shareWrite, final boolean sharePublic) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
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
                if (res.successful()) {
                    fetchFiles();

                    if (sharePublic) {
                        final android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(e);

                        dialog.setMessage("Send link?").setPositiveButton("Send", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String htmlBody = userfrom + " wants to share a file with you.<br>Access it via the following link:<br><br>" + res.getMessage();
                                Spanned shareBody = Html.fromHtml(htmlBody);
                                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                                sharingIntent.setType("text/plain");
                                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "simpleDrive share link");
                                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                                startActivity(Intent.createChooser(sharingIntent, "Send via"));
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(RemoteFiles.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("label", res.getMessage());
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(e, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                                dialog.cancel();
                            }
                        }).show();
                    }
                } else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void unshare(final String target) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "unshare");
                con.addFormField("target", target);

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

    private void zip(final String target, final String source) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
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
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, "Error zipping", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void restore(final String target) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "restore");
                con.addFormField("target", target);

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

    private void delete(final String target, final boolean fullyDelete) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSwipeRefreshLayout.setRefreshing(true);
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
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void paste(final String source, final String target, final boolean cut) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSwipeRefreshLayout.setRefreshing(true);
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
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    clipboard = new JSONArray();
                    hidePaste();
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void getPermissions() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection con = new Connection("user", "admin");
                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful() && res.getMessage().equals("true")) {
                    isAdmin = true;
                    unhideDrawerItem(R.id.navigation_view_item_server);
                }
            }
        }.execute();
    }

    private void getVersion() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection con = new Connection("system", "version");

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    try {
                        JSONObject job = new JSONObject(res.getMessage());
                        String latest = job.getString("recent");
                        if (latest.length() > 0 && !latest.equals("null")) {
                            Toast.makeText(e, "Server update available", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }.execute();
    }

    private void rename(final String target, final String filename) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSwipeRefreshLayout.setRefreshing(true);
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
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
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