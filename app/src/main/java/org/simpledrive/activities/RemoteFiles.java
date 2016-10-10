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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
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
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
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
import org.simpledrive.helper.UploadManager;
import org.simpledrive.helper.Util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteFiles extends AppCompatActivity {
    // General
    public static boolean appVisible;
    public static RemoteFiles e;
    private static String username = "";
    private static String viewmode = "files";
    private static final String TMP_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private static int FORCE_RELOAD = 5;
    private static SharedPreferences settings;
    private static boolean longClicked = false;
    private static int loginAttempts = 0;
    private static boolean loadthumbs = false;
    private static int lastSelected = 0;
    private static int grids = 3;
    private static int gridSize;
    private static boolean forceFullLoad = true;
    private static boolean preventLock = false;
    private static boolean calledForUnlock = false;

    // Audio
    public static String audioFilename;
    private static AudioService mPlayerService;
    private static boolean mBound = false;
    private static final Handler mHandler = new Handler();
    private static boolean audioUpdateRunning = false;

    // Interface
    private static AbsListView list;
    private static int listLayout;
    private static int theme;
    private static Toolbar toolbar;
    private static Toolbar toolbarBottom;
    private static ActionMenuView amvMenu;
    private static Menu bottomContextMenu;
    private static TextView info;
    private static SwipeRefreshLayout mSwipeRefreshLayout;
    private static Menu mToolbarMenu;
    private static Menu mContextMenu;
    public static TextView audioTitle;
    public static RelativeLayout player;
    public static SeekBar seek;
    public static ImageView bExit;
    public static ImageView bPlay;
    public static TextView header_user;
    public static TextView header_server;
    public static TextView header_indicator;
    private static DrawerLayout mDrawerLayout;
    private static NavigationView mNavigationView;
    private static GridView tmp_grid;
    private static ListView tmp_list;
    private static FloatingActionButton fab;
    private static FloatingActionButton fab_file;
    private static FloatingActionButton fab_folder;
    private static FloatingActionButton fab_upload;
    private static FloatingActionButton fab_paste;
    private static FloatingActionButton fab_paste_cancel;
    private static SearchView searchView = null;
    private static boolean accountsVisible = false;
    private static ActionMode actionMode;
    private static boolean bottomToolbarEnabled = false;

    // Files
    private static ArrayList<FileItem> items = new ArrayList<>();
    private static ArrayList<FileItem> filteredItems = new ArrayList<>();
    private static ArrayList<FileItem> hierarchy = new ArrayList<>();
    private static FileAdapter newAdapter;
    private static int sortOrder = 1;
    private static JSONArray clipboard = new JSONArray();
    private static boolean deleteAfterCopy = false;

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

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;
        forceFullLoad = true;

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        theme = (settings.getString("darktheme", "").length() == 0 || !Boolean.valueOf(settings.getString("darktheme", ""))) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        e.setTheme(theme);

        listLayout = (settings.getString("listlayout", "").length() == 0 || settings.getString("listlayout", "").equals("list")) ? R.layout.filelist : R.layout.filegrid;
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
        createTmpFolder();
    }

    protected void onResume() {
        super.onResume();

        preventLock = false;
        appVisible = true;
        loadthumbs = (settings.getString("loadthumb", "").length() == 0) ? false : Boolean.valueOf(settings.getString("loadthumb", ""));
        bottomToolbarEnabled = (settings.getString("bottomtoolbar", "").length() == 0) ? false : Boolean.valueOf(settings.getString("bottomtoolbar", ""));

        if (!CustomAuthenticator.enable(e)) {
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
            startActivityForResult(new Intent(e.getApplicationContext(), Unlock.class), 4);
        }
        else if (forceFullLoad || CustomAuthenticator.activeAccountChanged) {
            forceFullLoad = false;
            connect();
        }

        updateNavigationDrawer();
        username = CustomAuthenticator.getUsername();
        header_user.setText(username);
        header_server.setText(CustomAuthenticator.getServer());
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

        // Highlight current view
        switch (viewmode) {
            case "trash":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_trash);
                break;
            case "sharedbyme":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_sharedbyme);
                break;
            case "sharedwithme":
                mNavigationView.setCheckedItem(R.id.navigation_view_item_sharedwithme);
                break;
            default:
                mNavigationView.setCheckedItem(R.id.navigation_view_item_files);
                break;
        }
    }

    protected void onPause() {
        if (!preventLock) {
            calledForUnlock = false;
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
                UploadManager.addUpload(e, ul_paths, hierarchy.get(hierarchy.size() - 1).getJSON().toString(), new UploadManager.Upload.TaskListener() {
                    @Override
                    public void onFinished() {
                        fetchFiles();
                    }
                });
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
            viewmode = "files";
            fetchFiles();
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
        SearchManager searchManager = (SearchManager) e.getSystemService(Context.SEARCH_SERVICE);

        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
        }
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(e.getComponentName()));

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
        amvMenu = (ActionMenuView) toolbarBottom.findViewById(R.id.amvMenu);

        tmp_grid = (GridView) findViewById(R.id.grid);
        tmp_list = (ListView) findViewById(R.id.list);

        bPlay = (ImageView) e.findViewById(R.id.bPlay);
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.togglePlay();
            }
        });

        bExit = (ImageView) e.findViewById(R.id.bExit);
        bExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.stop();
            }
        });

        player = (RelativeLayout) findViewById(R.id.audioplayer);
        audioTitle = (TextView) e.findViewById(R.id.audio_title);

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
                Intent result = new Intent(RemoteFiles.this, FileSelector.class);
                startActivityForResult(result, 1);
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
                paste(clipboard.toString(), hierarchy.get(hierarchy.size() - 1).getJSON().toString(), deleteAfterCopy);
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

    private static void showPaste() {
        fab.setVisibility(View.GONE);
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);

        fab_paste.setVisibility(View.VISIBLE);
        fab_paste_cancel.setVisibility(View.VISIBLE);
    }

    private static void hidePaste() {
        fab.setVisibility(View.VISIBLE);

        fab_paste.setVisibility(View.GONE);
        fab_paste_cancel.setVisibility(View.GONE);
    }

    public static void fetchFiles() {
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
                Connection con = new Connection("files", "list");
                con.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
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
    private static void extractFiles(String rawJSON) {
        // Reset anything related to listing files
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);
        fab_upload.setVisibility(View.GONE);

        int thumbSize = (listLayout == R.layout.filelist) ? Util.dpToPx(40) : gridSize;
        items = new ArrayList<>();
        filteredItems = new ArrayList<>();

        try {
            JSONArray jar = new JSONArray(rawJSON);

            for (int i = 0; i < jar.length(); i++){
                JSONObject obj = jar.getJSONObject(i);

                String filename = (viewmode.equals("trash")) ? obj.getString("filename").substring(0, obj.getString("filename").lastIndexOf("_trash")) : obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));
                String hash = obj.getString("hash");
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((obj.getString("rootshare").length() == 0) ? "" : "shared");
                Bitmap icon;

                switch (type) {
                    case "folder":
                        icon = BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_folder);
                        break;
                    case "audio":
                        icon = BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_audio);
                        break;
                    case "pdf":
                        icon = BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_pdf);
                        break;
                    case "image":
                        icon = BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_image);
                        break;
                    default:
                        icon = BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_unknown);
                        break;
                }

                Bitmap thumb = null;
                String thumbPath = "";
                String imgPath = "";

                if (type.equals("image")) {
                    String ext = "." + FilenameUtils.getExtension(filename);
                    imgPath = TMP_FOLDER + Util.md5(parent + filename) + ext;
                    String thumbType = (listLayout == R.layout.filelist) ? "list" : "grid";
                    thumbPath = TMP_FOLDER + Util.md5 (parent + filename) + "_" + thumbType + ".jpg";


                    if (new File(imgPath).exists()) {
                        thumb = Util.getThumb(imgPath, thumbSize);
                    }
                    else if (new File(thumbPath).exists()) {
                        thumb = Util.getThumb(thumbPath, thumbSize);
                    }
                }


                FileItem item = new FileItem(obj, filename, parent, null, size, obj.getString("edit"), type, owner, hash, icon, thumb, thumbPath, imgPath);
                items.add(item);
                filteredItems.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(e, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static void displayFiles() {
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

        newAdapter = new FileAdapter(e, listLayout, list, gridSize, loadthumbs, firstFilePos);
        newAdapter.setData(filteredItems);
        list.setAdapter(newAdapter);

        // Show current directory in toolbar
        String title;
        FileItem thisFolder = hierarchy.get(hierarchy.size() - 1);
        if (!thisFolder.getFilename().equals("")) {
            title = thisFolder.getFilename();
        }
        else if (viewmode.equals("sharedbyme")) {
            title = "My shares";
        }
        else if (viewmode.equals("sharedwithme")) {
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

    public void openFile(int position) {
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
            Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
            i.putExtra("position", getCurrentImage(item.getFilename()));
            e.startActivity(i);
        }
        else if (item.is("audio") && mPlayerService != null) {
            Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
            audioFilename = item.getFilename();

            try {
                URI uri = new URI(CustomAuthenticator.getServer() + "api/files/read?target=[" + URLEncoder.encode(filteredItems.get(position).getJSON().toString(), "UTF-8") + "]&token=" + CustomAuthenticator.getToken());
                mPlayerService.initPlay(uri.toASCIIString());
                showAudioPlayer();
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else if (item.is("text")) {
            preventLock = true;
            Intent i = new Intent(e.getApplicationContext(), Editor.class);
            i.putExtra("file", filteredItems.get(position).getJSON().toString());
            i.putExtra("filename", filteredItems.get(position).getFilename());
            e.startActivity(i);
        }
        else {
            Toast.makeText(e, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
    }

    public void toggleFAB(boolean hide) {
        int visible = (hide) ? View.GONE : View.VISIBLE;
        fab.setVisibility(visible);
        fab_file.setVisibility(View.GONE);
        fab_folder.setVisibility(View.GONE);
        fab_upload.setVisibility(View.GONE);
    }

    public void showBottomToolbar() {
        toolbarBottom.setVisibility(View.VISIBLE);
    }

    private void hideBottomToolbar() {
        toolbarBottom.setVisibility(View.GONE);
    }

    public void showAudioPlayer() {
        player.setVisibility(View.VISIBLE);
        audioTitle.setText(audioFilename);

        if (!audioUpdateRunning) {
            startUpdateLoop();
        }
    }

    public void hideAudioPlayer() {
        player.setVisibility(View.GONE);
    }

    public void startUpdateLoop() {
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

    public int getCurrentImage(String filename) {
        ArrayList<FileItem> allImages = getAllImages();
        for (int i = 0; i < allImages.size(); i++) {
            if (filename.equals(allImages.get(i).getFilename())) {
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

    public static boolean isItemSelected(int pos) {
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
    private static void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    private static String getAllSelected() {
        JSONArray arr = new JSONArray();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                arr.put(filteredItems.get(i).getJSON());
            }
        }

        return arr.toString();
    }

    public static String getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return filteredItems.get(i).getJSON().toString();
            }
        }
        return "";
    }

    public static void connect() {
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
                    try {
                        hierarchy = new ArrayList<>();

                        JSONObject currDirJSON = new JSONObject();
                        currDirJSON.put("path", "");
                        currDirJSON.put("rootshare", "");

                        FileItem currDir = new FileItem(currDirJSON, "", "");
                        hierarchy.add(currDir);

                        CustomAuthenticator.updateToken(res.getMessage());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    fetchFiles();
                    getVersion();
                    getPermissions();
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

    public static void unhideDrawerItem(int id) {
        Menu navMenu = mNavigationView.getMenu();
        navMenu.findItem(id).setVisible(true);
    }

    private void showShare(final String target) {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(e);
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
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("New " + type.substring(0,1).toUpperCase() + type.substring(1));

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);

        alert.setPositiveButton("Create", new DialogInterface.OnClickListener() {
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

    private void showRename(final String filename, final String target) {
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("Rename");

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);
        String fn_without_ext = (filename.lastIndexOf('.') != -1) ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        input.setText(fn_without_ext);

        alert.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            rename(input.getText().toString(), target);
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
                InputMethodManager m = (InputMethodManager) e.getSystemService(Context.INPUT_METHOD_SERVICE);

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
                        viewmode = "files";
                        item.setChecked(true);
                        fetchFiles();
                        break;

                    case R.id.navigation_view_item_sharedbyme:
                        viewmode = "sharedbyme";
                        item.setChecked(true);
                        clearHierarchy();
                        fetchFiles();
                        break;

                    case R.id.navigation_view_item_sharedwithme:
                        viewmode = "sharedwithme";
                        item.setChecked(true);
                        clearHierarchy();
                        fetchFiles();
                        break;

                    case R.id.navigation_view_item_trash:
                        item.setChecked(true);
                        openTrash();
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
                        CustomAuthenticator.setActive(item.getTitle().toString());
                        finish();
                        startActivity(getIntent());
                }

                return true;
            }
            }
        );
    }

    private void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null && theme == R.style.MainTheme_Dark) {
            toolbar.setPopupTheme(R.style.MainTheme_Dark);
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
                switch (menuItem.getItemId()) {
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
                        restore(hierarchy.get(0).getJSON().toString(), getAllSelected());
                        actionMode.finish();
                        break;

                    case R.id.copy:
                        if (deleteAfterCopy) {
                            clipboard = new JSONArray();
                        }

                        deleteAfterCopy = false;

                        for (int i = 0; i < filteredItems.size(); i++) {
                            if (list.isItemChecked(i)) {
                                clipboard.put(filteredItems.get(i).getJSON());
                            }
                        }

                        Toast.makeText(e, clipboard.length() + " files to copy", Toast.LENGTH_SHORT).show();
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
                                clipboard.put(filteredItems.get(i).getJSON());
                            }
                        }

                        Toast.makeText(e, clipboard.length() + " files to cut", Toast.LENGTH_SHORT).show();
                        showPaste();
                        actionMode.finish();
                        break;

                    case R.id.download:
                        DownloadManager.addDownload(e, getAllSelected());
                        actionMode.finish();
                        break;

                    case R.id.zip:
                        zip(hierarchy.get(hierarchy.size() - 1).getJSON().toString(), getAllSelected());
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
                return onOptionsItemSelected(menuItem);
            }
        });
        // Inflate a menu to be displayed in the toolbar
        MenuInflater inflater = getMenuInflater();
        bottomContextMenu = amvMenu.getMenu();
        inflater.inflate(R.menu.remote_files_toolbar_bottom, bottomContextMenu);
    }

    private static void setToolbarTitle(final String title) {
        if (e.getSupportActionBar() != null) {
            e.getSupportActionBar().setTitle(title);
        }
    }

    private static void setToolbarSubtitle(final String subtitle) {
        if (e.getSupportActionBar() != null) {
            e.getSupportActionBar().setSubtitle(subtitle);
        }
    }

    public void initList() {
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

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
                switch (item.getItemId()) {
                    case R.id.selectall:
                        selectAll();
                        break;

                    case R.id.rename:
                        showRename(filteredItems.get(lastSelected).getFilename(), getFirstSelected());
                        mode.finish();
                        break;

                    case R.id.delete:
                        delete(getAllSelected(), viewmode.equals("trash"));
                        mode.finish();
                        break;

                    case R.id.restore:
                        restore(hierarchy.get(0).getJSON().toString(), getAllSelected());
                        mode.finish();
                        break;

                    case R.id.copy:
                        if (deleteAfterCopy) {
                            clipboard = new JSONArray();
                        }

                        deleteAfterCopy = false;

                        for (int i = 0; i < filteredItems.size(); i++) {
                            if (list.isItemChecked(i)) {
                                clipboard.put(filteredItems.get(i).getJSON());
                            }
                        }

                        Toast.makeText(e, clipboard.length() + " files to copy", Toast.LENGTH_SHORT).show();
                        showPaste();
                        mode.finish();
                        break;

                    case R.id.cut:
                        if (!deleteAfterCopy) {
                            clipboard = new JSONArray();
                        }

                        deleteAfterCopy = true;

                        for (int i = 0; i < filteredItems.size(); i++) {
                            if (list.isItemChecked(i)) {
                                clipboard.put(filteredItems.get(i).getJSON());
                            }
                        }

                        Toast.makeText(e, clipboard.length() + " files to cut", Toast.LENGTH_SHORT).show();
                        showPaste();
                        mode.finish();
                        break;

                    case R.id.download:
                        DownloadManager.addDownload(e, getAllSelected());
                        mode.finish();
                        break;

                    case R.id.zip:
                        zip(hierarchy.get(hierarchy.size() - 1).getJSON().toString(), getAllSelected());
                        mode.finish();
                        break;

                    case R.id.share:
                        showShare(getFirstSelected());
                        mode.finish();
                        break;

                    case R.id.unshare:
                        unshare(getFirstSelected());
                        mode.finish();
                        break;
                }
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                if (longClicked) {
                    longClicked = false;
                    return;
                }

                boolean trash = viewmode.equals("trash");
                mContextMenu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);

                if (bottomToolbarEnabled) {
                    bottomContextMenu.findItem(R.id.restore).setVisible(trash);
                    bottomContextMenu.findItem(R.id.download).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.copy).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.cut).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.zip).setVisible(!trash);
                    bottomContextMenu.findItem(R.id.rename).setVisible(!trash && list.getCheckedItemCount() == 1);
                    bottomContextMenu.findItem(R.id.share).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).getHash().length() == 0 && filteredItems.get(position).getOwner().equals(""));
                    bottomContextMenu.findItem(R.id.unshare).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).getHash().length() > 0);
                }
                else {
                    mContextMenu.findItem(R.id.restore).setVisible(trash);
                    mContextMenu.findItem(R.id.download).setVisible(!trash);
                    mContextMenu.findItem(R.id.delete).setVisible(true);
                    mContextMenu.findItem(R.id.copy).setVisible(!trash);
                    mContextMenu.findItem(R.id.cut).setVisible(!trash);
                    mContextMenu.findItem(R.id.zip).setVisible(!trash);
                    mContextMenu.findItem(R.id.rename).setVisible(!trash && list.getCheckedItemCount() == 1);
                    mContextMenu.findItem(R.id.share).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).getHash().length() == 0 && filteredItems.get(position).getOwner().equals(""));
                    mContextMenu.findItem(R.id.unshare).setVisible(!trash && list.getCheckedItemCount() == 1 && filteredItems.get(position).getHash().length() > 0);
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

    public void setListLayout(int layout) {
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

    public void clearHierarchy() {
        if (hierarchy.size() > 0) {
            FileItem first = hierarchy.get(0);
            hierarchy = new ArrayList<>();
            hierarchy.add(first);
        }
    }

    public void openTrash() {
        clearHierarchy();
        viewmode = "trash";
        fetchFiles();
    }

    private void createTmpFolder() {
        // Create image cache folder
        File tmp = new File(TMP_FOLDER);
        if (!tmp.exists()) {
            tmp.mkdir();
        }
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

    private static void create(final String target, final String filename, final String type) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
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

    private static void share(final String target, final String userfrom, final String userto, final boolean shareWrite, final boolean sharePublic) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
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
                e.setProgressBarIndeterminateVisibility(false);
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
                                e.startActivity(Intent.createChooser(sharingIntent, "Send via"));
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ClipboardManager clipboard = (ClipboardManager) e.getSystemService(RemoteFiles.CLIPBOARD_SERVICE);
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

    private static void unshare(final String target) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "unshare");
                con.addFormField("target", target);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                e.setProgressBarIndeterminateVisibility(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void zip(final String target, final String source) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
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
                e.setProgressBarIndeterminateVisibility(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, "Error zipping", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void restore(final String target, final String source) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "move");
                con.addFormField("target", target);
                con.addFormField("trash", "true");
                con.addFormField("source", source);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                e.setProgressBarIndeterminateVisibility(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void delete(final String target, final boolean fullyDelete) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("files", "delete");
                con.addFormField("target", target);
                con.addFormField("final", Boolean.toString(fullyDelete));

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                e.setProgressBarIndeterminateVisibility(false);

                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void paste(final String source, final String target, final boolean cut) {
        new AsyncTask<Void, Void, Connection.Response>() {
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
                e.setProgressBarIndeterminateVisibility(false);

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

    private static void getPermissions() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection con = new Connection("users", "admin");
                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful() && res.getMessage().equals("true")) {
                    unhideDrawerItem(R.id.navigation_view_item_server);
                }
            }
        }.execute();
    }

    private static void getVersion() {
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

    private static void rename(final String target, final String filename) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
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
                e.setProgressBarIndeterminate(false);
                if (res.successful()) {
                    fetchFiles();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    public static void hideAccounts() {
        accountsVisible = false;
        header_indicator.setText("\u25BC");
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts, false);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_accounts_management, false);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_one, true);
        mNavigationView.getMenu().setGroupVisible(R.id.navigation_drawer_group_two, true);
    }

    public void toggleAccounts() {
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