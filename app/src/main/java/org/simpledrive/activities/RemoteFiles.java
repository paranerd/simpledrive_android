package org.simpledrive.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
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

import org.simpledrive.helper.AudioService;
import org.simpledrive.helper.AudioService.LocalBinder;
import org.simpledrive.helper.Connection;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.helper.FileItem;
import org.simpledrive.helper.Util;

public class RemoteFiles extends AppCompatActivity {
    // General
    public static boolean appVisible;
    public static RemoteFiles e;
    private static String username = "";
    private static String viewmode = "files";
    private static final String tmpFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private SharedPreferences settings;
    private boolean longClicked = false;
    private static int loginAttempts = 0;
    private boolean loadthumbs = false;
    private int lastSelected = 0;
    private static int grids = 3;
    private static int gridSize;

    // Audio
    public static String audioFilename;
    private static AudioService mPlayerService;
    private boolean mBound = false;
    private final Handler mHandler = new Handler();
    private boolean audioUpdateRunning = false;

    // Upload
    public static int uploadCurrent = 0;
    public static int uploadTotal = 0;
    public static int uploadSuccessful = 0;
    public static boolean uploading = false;
    private ArrayList<HashMap<String, String>> uploadQueue = new ArrayList<>();

    // Interface
    private static AbsListView list;
    private static String globLayout;
    private TextView info;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private Menu mToolbarMenu;
    private Menu mContextMenu;
    public static TextView audioTitle;
    public static RelativeLayout player;
    public static SeekBar seek;
    public static ImageButton bPlay, bExit;
    public static TextView header_user;
    public static TextView header_server;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private GridView tmp_grid;
    private ListView tmp_list;
    private FloatingActionButton fab;
    private FloatingActionButton fab_file;
    private FloatingActionButton fab_folder;
    private FloatingActionButton fab_upload;
    private SearchView searchView = null;

    // Files
    private static ArrayList<FileItem> items = new ArrayList<>();
    private static ArrayList<FileItem> filteredItems = new ArrayList<>();
    private static ArrayList<FileItem> hierarchy = new ArrayList<>();
    private FileAdapter newAdapter;
    private int sortOrder = 1;

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

    private class ListContent extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (newAdapter != null) {
                newAdapter.cancelThumbLoad();
            }

            mSwipeRefreshLayout.setRefreshing(true);
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("files", "list", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            multipart.addFormField("mode", viewmode);

            return multipart.finish();
    	}

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null || !value.get("status").equals("ok")) {
                String msg = (value == null) ? getResources().getString(R.string.unknown_error) : value.get("msg");
                info.setVisibility(View.VISIBLE);
                info.setText(msg);
                new Connect().execute();
            }
            else {
                loginAttempts = 0;
                extractFiles(value.get("msg"));
                displayFiles();
            }
        }
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

        int thumbSize = (globLayout.equals("list")) ? Util.dpToPx(40) : gridSize;
        items = new ArrayList<>();
        filteredItems = new ArrayList<>();

        try {
            JSONArray jar = new JSONArray(rawJSON);

            for(int i = 0; i < jar.length(); i++){
                JSONObject obj = jar.getJSONObject(i);

                String filename = (viewmode.equals("trash")) ? obj.getString("filename").substring(0, obj.getString("filename").lastIndexOf("_trash")) : obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Util.convertSize(obj.getString("size"));

                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : ((obj.getString("rootshare").length() == 0) ? "" : "shared");
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
                    default:
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unknown);
                        break;
                }

                Bitmap thumb = null;
                String thumbPath = "";
                String imgPath = "";

                if (type.equals("image")) {
                    String ext = "." + FilenameUtils.getExtension(filename);
                    imgPath = tmpFolder + Util.md5(parent + filename) + ext;
                    thumbPath = tmpFolder + Util.md5 (parent + filename) + "_" + globLayout + ".jpg";


                    if (new File(imgPath).exists()) {
                        thumb = Util.getThumb(imgPath, thumbSize);
                    }
                    else if (new File(thumbPath).exists()) {
                        thumb = Util.getThumb(thumbPath, thumbSize);
                    }
                }


                FileItem item = new FileItem(obj, filename, parent, null, size, obj.getString("edit"), type, owner, icon, thumb, thumbPath, imgPath);
                items.add(item);
                filteredItems.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(e, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayFiles() {
        Util.sortFilesByName(filteredItems, sortOrder);

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

        int layout = (globLayout.equals("list")) ? R.layout.filelist : R.layout.filegrid;
        newAdapter = new FileAdapter(e, layout, list, gridSize, loadthumbs, firstFilePos);
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
        else if(viewmode.equals("trash")) {
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
        if(viewmode.equals("trash")) {
            return;
        }
        else if(item.is("folder")) {
            hierarchy.add(item);
            new ListContent().execute();
        }
        else if(item.is("image")) {
            Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
            i.putExtra("position", getCurrentImage(item.getFilename()));
            e.startActivity(i);
        }
        else if(item.is("audio") && mPlayerService != null) {
            Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
            audioFilename = item.getFilename();

            try {
                URI uri = new URI(Connection.getServer() + "api/files/read?target=[" + URLEncoder.encode(filteredItems.get(position).getJSON().toString(), "UTF-8") + "]&token=" + Connection.token);
                mPlayerService.initPlay(uri.toASCIIString());
                showAudioPlayer();
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else if (item.is("text")) {
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

    public void showAudioPlayer() {
        player.setVisibility(View.VISIBLE);
        audioTitle.setText(audioFilename);
        toggleFAB(true);

        if (!audioUpdateRunning) {
            startUpdateLoop();
        }
    }

    public void hideAudioPlayer() {
        player.setVisibility(View.GONE);
        toggleFAB(false);
    }

    public void startUpdateLoop() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                audioUpdateRunning = true;
                if(AudioService.isPlaying()) {
                    int pos = mPlayerService.getCurrentPosition();

                    seek.setProgress(pos);
                    bPlay.setBackgroundResource(R.drawable.ic_pause);
                }
                else {
                    bPlay.setBackgroundResource(R.drawable.ic_play);
                }

                if(AudioService.isActive() && appVisible) {
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
        for(int i = 0; i < allImages.size(); i++) {
            if(filename.equals(allImages.get(i).getFilename())) {
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
     * Removes all selected Elements
     */

    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    private JSONArray getAllSelected() {
        JSONArray arr = new JSONArray();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                arr.put(filteredItems.get(i).getJSON());
            }
        }

        return arr;
    }

    public JSONObject getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return filteredItems.get(i).getJSON();
            }
        }
        return null;
    }

    public class Connect extends AsyncTask<String, String, HashMap<String, String>> {
        Account[] sc;
        AccountManager accMan = AccountManager.get(RemoteFiles.this);

    	@Override
        protected void onPreExecute() {
            loginAttempts++;
            super.onPreExecute();
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... login) {
            sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttempts > 2) {
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
             header_user.setText(username);
             header_server.setText(Connection.getServer());

             if(value.get("status").equals("ok")) {
                 try {
                     hierarchy = new ArrayList<>();

                     JSONObject currDirJSON = new JSONObject();
                     currDirJSON.put("path", "");
                     currDirJSON.put("rootshare", "");

                     FileItem currDir = new FileItem(currDirJSON, "", "");
                     hierarchy.add(currDir);

                     Connection.setToken(value.get("msg"));
                     accMan.setUserData(sc[0], "token", value.get("msg"));
                 } catch (JSONException e) {
                     e.printStackTrace();
                 }

                 new ListContent().execute();
                 new GetVersion().execute();
                 new GetPermissions().execute();
             }
             else {
                 if (sc.length == 0) {
                     // No account, return to login
                     Intent i = new Intent(e.getApplicationContext(), Login.class);
                     e.startActivity(i);
                 }
                 else {
                     // No connection
                     info.setVisibility(View.VISIBLE);
                     info.setText(R.string.reconnect_error);

                     if (newAdapter != null) {
                         newAdapter.setData(null);
                         newAdapter.notifyDataSetChanged();
                     }

                     mSwipeRefreshLayout.setRefreshing(false);
                     mSwipeRefreshLayout.setEnabled(true);
                 }
             }
    	 }
    }

    private void unhideDrawerItem(int id) {
        Menu navMenu = mNavigationView.getMenu();
        navMenu.findItem(id).setVisible(true);
    }

    private class GetPermissions extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("users", "admin", null);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok") && value.get("msg").equals("true")) {
                unhideDrawerItem(R.id.navigation_view_item_server);
            }
        }
    }

    private class GetVersion extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("system", "version", null);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                try {
                    JSONObject job = new JSONObject(value.get("msg"));
                    String latest = job.getString("recent");
                    if (latest.length() > 0 && !latest.equals("null")) {
                        Toast.makeText(e, "Server update available", Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                ArrayList<String> ul_paths = new ArrayList<>();

                String[] paths = data.getStringArrayExtra("paths");
                Collections.addAll(ul_paths, paths);
                upload_handler(ul_paths);
            }
        }
    }

    public void onBackPressed() {
        if(getAllSelected().length() != 0) {
            unselectAll();
        }
        else if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new ListContent().execute();
        }
        else if(viewmode.equals("trash")) {
            viewmode = "files";
            new ListContent().execute();
        }
        else {
            super.onBackPressed();
        }
    }

    private void download(String target) {
        Download dl = new Download();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            dl.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, target);
        }
        else {
            dl.execute(target);
        }
    }

    private void showShare(final String target) {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(e);
        View shareView = View.inflate(this, R.layout.dialog_share, null);
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
                    new Share(shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked()).execute(target);
                    dialog2.dismiss();
                }
            }
        });

        shareUser.requestFocus();
        showVirtualKeyboard();
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

    private void showRename(final String filename, final String target) {
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("Rename");

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);
        String fn_without_ext = (filename.lastIndexOf('.') != -1) ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        input.setText(fn_without_ext);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            String newFilename = input.getText().toString();
            new Rename().execute(newFilename, target);
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

    private class Rename extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(false);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... names) {
            Connection multipart = new Connection("files", "rename", null);
            multipart.addFormField("newFilename", names[0]);
            multipart.addFormField("target", names[1]);

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

    private class Download extends AsyncTask<String, Integer, HashMap<String, String>> {
        private NotificationCompat.Builder mBuilder;
        private NotificationManager mNotifyManager;
        private int notificationId = 2;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Intent intent = new Intent(e, RemoteFiles.class);
            PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

            mBuilder = new NotificationCompat.Builder(e)
                    .setContentTitle("Downloading...")
                    .setContentText("Download in progress")
                    .setContentIntent(pIntent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_cloud)
                    .setProgress(100, 0, false);
            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected HashMap<String, String> doInBackground(String... target) {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

            Connection multipart = new Connection("files", "read", new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    publishProgress(num);
                }
            });

            multipart.addFormField("target", target[0]);
            multipart.setDownloadPath(path, null);
            return multipart.finish();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            mBuilder.setProgress(100, values[0], false);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            mBuilder.setContentTitle("Download complete")
                    .setOngoing(false)
                    .setContentText("")
                    .setProgress(0,0,false);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }
  }

    private class Delete extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
           protected void onPreExecute() {
               super.onPreExecute();
               e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
           protected HashMap<String, String> doInBackground(String... target) {
            Connection multipart = new Connection("files", "delete", null);
            multipart.addFormField("target", target[0]);
            multipart.addFormField("final", Boolean.toString(viewmode.equals("trash")));

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);

            if(value.get("status").equals("ok")) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Share extends AsyncTask<String, String, HashMap<String, String>> {
        String shareUser;
        int shareWrite;
        int sharePublic;

        public Share(String shareUser, boolean shareWrite, boolean sharePublic) {
            this.shareUser = shareUser;
            this.shareWrite = (shareWrite) ? 1 : 0;
            this.sharePublic = (sharePublic) ? 1 : 0;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... target) {
            Connection multipart = new Connection("files", "share", null);
            multipart.addFormField("target", target[0]);
            multipart.addFormField("mail", "");
            multipart.addFormField("key", "");
            multipart.addFormField("userto", shareUser);
            multipart.addFormField("pubAcc", Integer.toString(sharePublic));
            multipart.addFormField("write", Integer.toString(shareWrite));

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(final HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                new ListContent().execute();
                if(this.sharePublic == 1) {
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(e);

                    dialog.setMessage("Send link?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String htmlBody = username + " wants to share a file with you.<br>Access it via the following link:<br><br>" + value;
                            Spanned shareBody = Html.fromHtml(htmlBody);
                            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "simpleDrive share link");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                            startActivity(Intent.createChooser(sharingIntent, "Send via"));
                        }
                    }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("label", value.get("msg"));
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(e, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                        }
                    }).show();
                }
            } else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Unshare extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... target) {
            Connection multipart = new Connection("files", "unshare", null);
            multipart.addFormField("target", target[0]);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Zip extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... source) {
            Connection multipart = new Connection("files", "zip", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            multipart.addFormField("source", source[0]);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, "Error zipping", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Restore extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... source) {
            Connection multipart = new Connection("files", "move", null);
            multipart.addFormField("target", hierarchy.get(0).getJSON().toString());
            multipart.addFormField("trash", "true");
            multipart.addFormField("source", source[0]);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String>value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
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
            fullCurrent = ShareFiles.uploadCurrent + uploadCurrent;
            fullTotal = ShareFiles.uploadTotal + uploadTotal;

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
            fullSuccessful = ShareFiles.uploadSuccessful + uploadSuccessful;
            if(uploadQueue.size() > 0) {
                new Upload().execute();
            }
            else {
                if(!ShareFiles.uploading) {
                    String file = (fullTotal == 1) ? "file" : "files";
                    mBuilder.setContentTitle("Upload complete")
                            .setContentText(fullSuccessful + " of " + fullTotal + " " + file + " added")
                            .setOngoing(false)
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(notificationId, mBuilder.build());
                    uploading = false;
                }
                new ListContent().execute();
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
            if(file.isDirectory()) {
                upload_add_recursive(file.getParent(), file, hierarchy.get(hierarchy.size() - 1).getJSON().toString());
            } else {
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

    private void setUpDrawer() {
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                supportInvalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        header_user = (TextView) mNavigationView.getHeaderView(0).findViewById(R.id.navigation_drawer_header_user);
        header_server = (TextView) mNavigationView.getHeaderView(0).findViewById(R.id.navigation_drawer_header_server);

        mNavigationView
                .getHeaderView(0)
                .findViewById(R.id.navigation_drawer_header_clickable)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mDrawerLayout.closeDrawer(GravityCompat.START);
                    }
                });

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(final MenuItem item) {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                switch (item.getItemId()) {
                    case R.id.navigation_view_item_files:
                        viewmode = "files";
                        item.setChecked(true);
                        new ListContent().execute();
                        break;

                    case R.id.navigation_view_item_sharedbyme:
                        viewmode = "sharedbyme";
                        item.setChecked(true);
                        clearHierarchy();
                        new ListContent().execute();
                        break;

                    case R.id.navigation_view_item_sharedwithme:
                        viewmode = "sharedwithme";
                        item.setChecked(true);
                        clearHierarchy();
                        new ListContent().execute();
                        break;

                    case R.id.navigation_view_item_trash:
                        item.setChecked(true);
                        openTrash();
                        break;

                    case R.id.navigation_view_item_settings:
                        startActivity(new Intent(getApplicationContext(), AppSettings.class));
                        break;

                    case R.id.navigation_view_item_server:
                        startActivity(new Intent(getApplicationContext(), ServerSettings.class));
                        break;

                    case R.id.navigation_view_item_logout:
                        Connection.logout(e);
                        startActivity(new Intent(getApplicationContext(), Login.class));
                        finish();
                        break;
                }

                return true;
            }
            }
        );
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
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

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;
        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        setContentView(R.layout.activity_remotefiles);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        gridSize = displaymetrics.widthPixels / grids;

        tmp_grid = (GridView) findViewById(R.id.grid);
        tmp_list = (ListView) findViewById(R.id.list);

        bPlay = (ImageButton) e.findViewById(R.id.bPlay);
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.togglePlay();
                if (AudioService.isPlaying()) {
                    bPlay.setBackgroundResource(R.drawable.ic_pause);
                }
                else {
                    bPlay.setBackgroundResource(R.drawable.ic_play);
                }
            }
        });

        bExit = (ImageButton) e.findViewById(R.id.bExit);
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

        fab_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab_folder.setVisibility(View.GONE);
                fab_file.setVisibility(View.GONE);
                fab_upload.setVisibility(View.GONE);
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

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loginAttempts = 0;
                if (hierarchy.size() > 0) {
                    new ListContent().execute();

                }
                else {
                    new Connect().execute();
                }
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Util.dpToPx(56), Util.dpToPx(56) + 100);
        mSwipeRefreshLayout.setEnabled(true);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.DrawerLayout);
        mNavigationView = (NavigationView) findViewById(R.id.activity_main_navigation_view);

        // Prepare audio player
        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        if(AudioService.isPlaying()) {
            showAudioPlayer();
        }
        else {
            hideAudioPlayer();
        }

        loadthumbs = (settings.getString("loadthumb", "").length() == 0) ? false : Boolean.valueOf(settings.getString("loadthumb", ""));
        globLayout = (settings.getString("view", "").length() == 0 || settings.getString("view", "").equals("list")) ? "list" : "grid";
        setView(globLayout);

        setUpToolbar();
        setUpDrawer();
        setUpList();

        createTmpFolder();

        new Connect().execute();
    }

    private void createTmpFolder() {
        // Create image cache folder
        File tmp = new File(tmpFolder);
        if (!tmp.exists()) {
            tmp.mkdir();
        }
    }

    protected void onPause() {
        super.onPause();
        appVisible = false;
        unselectAll();
    }

    protected void onResume() {
        super.onResume();
        appVisible = true;
    }

    public void setUpList() {
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private int nr = 0;

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                toggleFAB(false);
                unselectAll();
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                nr = 0;
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.remote_files_context, menu);
                mContextMenu = menu;

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
                        showRename(filteredItems.get(lastSelected).getFilename(), getFirstSelected().toString());
                        mode.finish();
                        break;

                    case R.id.delete:
                        new Delete().execute(getAllSelected().toString());
                        mode.finish();
                        break;

                    case R.id.restore:
                        new Restore().execute(getAllSelected().toString());
                        mode.finish();
                        break;

                    case R.id.download:
                        download(getAllSelected().toString());
                        mode.finish();
                        break;

                    case R.id.zip:
                        new Zip().execute(getAllSelected().toString());
                        mode.finish();
                        break;

                    case R.id.share:
                        showShare(getFirstSelected().toString());
                        mode.finish();
                        break;

                    case R.id.unshare:
                        new Unshare().execute(getFirstSelected().toString());
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
                nr = (checked) ? ++nr : --nr;

                mContextMenu.findItem(R.id.restore).setVisible(trash);
                mContextMenu.findItem(R.id.selectall).setVisible(filteredItems.size() > 0);
                mContextMenu.findItem(R.id.download).setVisible(!trash);
                mContextMenu.findItem(R.id.unshare).setVisible(!trash);
                mContextMenu.findItem(R.id.zip).setVisible(!trash);
                mContextMenu.findItem(R.id.rename).setVisible(!trash);
                mContextMenu.findItem(R.id.share).setVisible(!trash && nr == 1);

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

    public void setView(String view) {
        globLayout = view;
        settings.edit().putString("view", globLayout).apply();

        if(globLayout.equals("list")) {
            list = (ListView) findViewById(R.id.list);
            tmp_grid.setVisibility(View.GONE);
        }
        else {
            list = (GridView) findViewById(R.id.grid);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        if(mToolbarMenu != null) {
            invalidateOptionsMenu();
        }
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mToolbarMenu = menu;

        if(globLayout.equals("list")) {
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
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectAll();
                                new Delete().execute(getAllSelected().toString());
                                unselectAll();
                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
                break;

            case R.id.toggle_view:
                String next_view = (globLayout.equals("grid")) ? "list" : "grid";
                setView(next_view);
                int layout = (globLayout.equals("list")) ? R.layout.filelist : R.layout.filegrid;
                //newAdapter = new FileAdapter(e, layout);
                newAdapter = new FileAdapter(e, layout, list, gridSize, loadthumbs, 0);
                newAdapter.setData(filteredItems);
                setUpList();
                list.setAdapter(newAdapter);
                break;
        }

        return true;
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
        new ListContent().execute();
    }
}