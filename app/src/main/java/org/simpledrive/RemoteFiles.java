package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.lib.AudioService;
import simpledrive.lib.AudioService.LocalBinder;
import simpledrive.lib.Helper;
import simpledrive.lib.Item;
import simpledrive.lib.MenuListAdapter;
import simpledrive.lib.Connection;

public class RemoteFiles extends ActionBarActivity {
    // General
    public static boolean appVisible;
    public static RemoteFiles e;
    private static String username = "";
    private static String mode = "files";
    private static final String tmpFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private SharedPreferences settings;
    private boolean longClicked = false;
    private static int loginAttempts = 0;
    private ArrayList<Item> thumbQueue = new ArrayList<>();
    private boolean thumbLoading = false;

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
    private TextView empty;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private ImageButton bUpload;
    private ImageButton bCreateFolder;
    private ImageButton bCreateFile;
    private Menu mMenu;
    public static TextView audioTitle;
    public static RelativeLayout player;
    public static RelativeLayout hoverButtons;
    public static SeekBar seek;
    public static ImageButton bPlay, bExit;
    private String titles[] = {"Files", "Trash", "Logout"};
    private int icons[] = {R.drawable.ic_folder, R.drawable.ic_trash, R.drawable.ic_logout};
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private LinearLayout mDrawerLinear;

    // Files
    private static ArrayList<Item> items = new ArrayList<>();
    private static ArrayList<JSONObject> hierarchy = new ArrayList<>();
    private Integer firstFilePos;
    private NewFileAdapter newAdapter;
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
            empty.setText("Loading files...");
            thumbQueue.clear();
            thumbLoading = false;

            mSwipeRefreshLayout.setRefreshing(true);
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("files", "list", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).toString());
            multipart.addFormField("mode", mode);

            return multipart.finish();
    	}

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null || !value.get("status").equals("ok")) {
                String msg = (value == null) ? "An error occurred" : value.get("msg");
                Toast.makeText(e, msg, Toast.LENGTH_SHORT).show();
                new Connect().execute();
            }
            else {
                loginAttempts = 0;
                displayFiles(value.get("msg"));
            }
        }
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void displayFiles(String rawJSON) {
        // Reset anything related to listing files
        bCreateFolder.setVisibility(View.GONE);
        bCreateFile.setVisibility(View.GONE);
        bUpload.setVisibility(View.GONE);

        int thumbSize;
        items = new ArrayList<>();

        if(globLayout.equals("list")) {
            thumbSize = Helper.dpToPx(40);
        } else {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            thumbSize = displaymetrics.widthPixels / 2;
        }

        try {
            JSONArray jar = new JSONArray(rawJSON);

            for(int i = 0; i < jar.length(); i++){
                JSONObject obj = jar.getJSONObject(i);

                String filename = (mode.equals("trash")) ? obj.getString("filename").substring(0, obj.getString("filename").lastIndexOf("_trash")) : obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? ((obj.getString("size").equals("1")) ? obj.getString("size") + " element" : obj.getString("size") + " elements") : Helper.convertSize(obj.getString("size"));

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

                if (type.equals("image")) {
                    String ext = FilenameUtils.getExtension(filename);
                    String imgPath = tmpFolder + Helper.md5(parent + filename) + ext;
                    String thumbPath = (globLayout.equals("list")) ? tmpFolder + Helper.md5(parent + filename) + "_list.jpg" : tmpFolder + Helper.md5(parent + filename) + "_grid.jpg";

                    if (new File(imgPath).exists()) {
                        thumb = Helper.getThumb(imgPath, thumbSize);
                    }
                    else if (new File(thumbPath).exists()) {
                        thumb = Helper.getThumb(thumbPath, thumbSize);
                    }
                }


                Item item = new Item(obj, filename, parent, null, size, obj.getString("edit"), type, owner, obj.getString("hash"), icon, thumb);
                items.add(item);
            }

            sortByName();

            firstFilePos = items.size();

            for(int i = 0; i < items.size(); i++) {
                if (!items.get(i).is("folder")) {
                    firstFilePos = i;
                    break;
                }
            }

            // Show current directory in toolbar
            String title;
            JSONObject hier = hierarchy.get(hierarchy.size() - 1);
            if(hier.has("filename")) {
                title = hier.getString("filename");
            }
            else if(mode.equals("trash")) {
                title = "Trash";
            }
            else {
                title = "Homefolder";
            }

            if(toolbar != null) {
                toolbar.setTitle(title);
                toolbar.setSubtitle("Folders: " + firstFilePos + ", Files: " + (items.size() - firstFilePos));
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(e, "An error occurred", Toast.LENGTH_SHORT).show();
        }

        String emptyText = (items.size() == 0) ? "Nothing to see here." : "";
        empty.setText(emptyText);

        int layout = (globLayout.equals("list")) ? R.layout.listview : R.layout.gridview;
        newAdapter = new NewFileAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);

        unselectAll();
    }

    private void sortByName() {
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item item1, Item item2) {
                if(item1.is("folder") && !item2.is("folder")) {
                    return -1;
                }
                if(!item1.is("folder") && item2.is("folder")) {
                    return 1;
                }
                return sortOrder * (item1.getFilename().toLowerCase().compareTo(item2.getFilename().toLowerCase()));
            }
        });
    }

    public class NewFileAdapter extends ArrayAdapter<Item> {
        private LayoutInflater layoutInflater;
        private int layout;

        public NewFileAdapter(Activity mActivity, int textViewResourceId) {
            super(mActivity, textViewResourceId);
            layoutInflater = LayoutInflater.from(mActivity);
            layout = textViewResourceId;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            final Item item = getItem(position);

            if(convertView == null) {
                convertView = layoutInflater.inflate(layout, null);
                convertView.setBackgroundResource(R.drawable.bkg_light);

                holder = new ViewHolder();
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
                holder.selector = (RelativeLayout) convertView.findViewById(R.id.selector);
                holder.check = (ImageView) convertView.findViewById(R.id.check);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.owner = (TextView) convertView.findViewById(R.id.owner);
                holder.separator = (TextView) convertView.findViewById(R.id.separator);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
                convertView.setBackgroundResource(R.drawable.bkg_light);
            }

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());
            holder.owner.setText(item.getOwner());
            holder.icon.setImageBitmap(item.getIcon());

            if(globLayout.equals("list")) {
                int visibility = (position == 0 || (firstFilePos != null && position == firstFilePos)) ? View.VISIBLE : View.GONE;
                holder.separator.setVisibility(visibility);

                String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
                holder.separator.setText(text);
            }

            if (item.isSelected()) {
                holder.check.setVisibility(View.VISIBLE);
                if(globLayout.equals("grid")) {
                    holder.selector.setBackgroundColor(getResources().getColor(R.color.transparentgreen));
                }
                convertView.setBackgroundColor(getResources().getColor(R.color.transparentgreen));
            }
            else {
                holder.check.setVisibility(View.INVISIBLE);
                holder.selector.setBackgroundColor(getResources().getColor(R.color.transparent));
                convertView.setBackgroundResource(R.drawable.bkg_light);
            }

            if(item.is("image")) {
                if (item.getThumb() == null) {
                    item.setThumb(BitmapFactory.decodeResource(getResources(), R.drawable.ic_image));
                    holder.thumb.setImageResource(R.drawable.ic_image);
                    String thumbPath = (globLayout.equals("list")) ? tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_list.jpg" : tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_grid.jpg";
                    item.setThumbPath(thumbPath);

                    if (!thumbLoading) {
                        //loadThumb(item);
                        thumbQueue.add(item);
                        new LoadThumb().execute();
                    } else {
                        thumbQueue.add(item);
                    }
                }
                else {
                    holder.thumb.setImageBitmap(item.getThumb());
                }
            }
            else {
                holder.thumb.setImageBitmap(null);
            }

            holder.selector.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getSelectedElem().length() > 0 || globLayout.equals("list")) {
                        toggleSelection(position);
                    } else {
                        openFile(position);
                    }
                }
            });

            holder.selector.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    longClicked = true;
                    toggleSelection(position);
                    return true;
                }
            });

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            ImageView thumb;
            ImageView check;
            TextView name;
            TextView size;
            TextView owner;
            TextView separator;
            RelativeLayout selector;
        }

        public void setData(ArrayList<Item> arg1) {
            clear();
            if(arg1 != null) {
                for (int i=0; i < arg1.size(); i++) {
                    add(arg1.get(i));
                }
            }
        }
    }

    private class LoadThumb extends AsyncTask<String, Integer, HashMap<String, String>> {
        Item item;
        String size;
        String filepath;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            thumbLoading = true;
        }

        @Override
        protected HashMap<String, String> doInBackground(String... info) {
            if (thumbQueue.size() > 0) {
                item = thumbQueue.remove(0);
            }
            else {
                return null;
            }

            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

            size = (globLayout.equals("list")) ? Helper.dpToPx(100) + "" : Integer.toString(displaymetrics.widthPixels / 2);
            String file = item.getJSON().toString();
            filepath = item.getThumbPath();

            File thumb = new File(filepath);

            Connection multipart = new Connection("files", "read", null);

            multipart.addFormField("target", "[" + file + "]");
            multipart.addFormField("width", size);
            multipart.addFormField("height", size);
            multipart.addFormField("type", "thumb");
            multipart.setDownloadPath(thumb.getParent(), thumb.getName());
            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if (value != null) {
                Bitmap bmp = Helper.getThumb(filepath, Integer.valueOf(size));

                thumbLoading = false;
                if (bmp != null && list != null) {
                    // Update adapter to display thumb
                    item.setThumb(bmp);
                    newAdapter.notifyDataSetChanged();
                }

                if (thumbQueue.size() > 0) {
                    new LoadThumb().execute();
                }

                /*if(filename.substring(filename.length() - 3).equals("png")) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos);
                } else {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                }*/
            }
        }
    }

    public void openFile(int position) {
        Item item = items.get(position);
        if(mode == "trash") {
            return;
        }
        else if(item.is("folder")) {
            hierarchy.add(item.getJSON());
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
            items.get(position).setSelected(true);
            try {
                URI uri = new URI(Connection.getServer() + "api/files/read?target=[" + URLEncoder.encode(items.get(position).getJSON().toString()) + "]&token=" + Connection.token);
                mPlayerService.initPlay(uri.toASCIIString());
                showAudioPlayer();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        else if (item.is("text")) {
            Intent i = new Intent(e.getApplicationContext(), Editor.class);
            i.putExtra("file", items.get(position).getJSON().toString());
            i.putExtra("filename", items.get(position).getFilename());
            e.startActivity(i);
        }
        else {
            Toast.makeText(e, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
    }

    public void showAudioPlayer() {
        player.setVisibility(View.VISIBLE);
        audioTitle.setText(audioFilename);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)hoverButtons.getLayoutParams();
        params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        hoverButtons.setLayoutParams(params);

        if (!audioUpdateRunning) {
            startUpdateLoop();
        }
    }

    public void hideAudioPlayer() {
        player.setVisibility(View.GONE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)hoverButtons.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        hoverButtons.setLayoutParams(params);
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
        ArrayList<HashMap<String, String>> allImages = getAllImages();
        for(int i = 0; i < allImages.size(); i++) {
            if(filename.equals(allImages.get(i).get("filename"))) {
                return i;
            }
        }
        return 0;
    }

    public static ArrayList<HashMap<String, String>> getAllImages() {
        ArrayList<HashMap<String, String>> images = new ArrayList<>();
        for(Item item : items) {
            if(item.is("image")) {
                String ext = "." + FilenameUtils.getExtension(item.getFilename());
                HashMap<String, String> img = new HashMap<>();
                img.put("file", item.getJSON().toString());
                img.put("filename", item.getFilename());
                img.put("path", tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + ext);
                String thumbPath = (globLayout.equals("list")) ? tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_list.jpg" : tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_grid.jpg";
                img.put("thumbPath", thumbPath);

                images.add(img);
            }
        }

        return images;
    }

    public void toggleSelection(int position) {
        items.get(position).toggleSelection();

        invalidateOptionsMenu();

        if(list != null) {
            newAdapter.notifyDataSetChanged();
        }
    }

    private void selectAll() {
        for(Item item : items) {
            item.setSelected(true);
        }
        invalidateOptionsMenu();
        if(list != null) {
            newAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Removes all selected Elements
     */

    private void unselectAll() {
        for(Item item : items) {
            item.setSelected(false);
        }
        invalidateOptionsMenu();
        if(list != null) {
            newAdapter.notifyDataSetChanged();
        }
    }

    public JSONObject getSelected() {
        for(Item item : items) {
            if(item.isSelected()) {
                return item.getJSON();
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
            empty.setText("Connecting ...");
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... login) {
            sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttempts > 2) {
                HashMap<String, String> map = new HashMap<>();
                map.put("status", "error");
                map.put("msg", "An error occurred");
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

                     JSONObject currDir = new JSONObject();
                     currDir.put("path", "");
                     currDir.put("rootshare", "");
                     hierarchy.add(currDir);

                     empty.setText("Nothing to see here.");
                     Connection.setToken(value.get("msg"));
                     accMan.setUserData(sc[0], "token", value.get("msg"));
                 } catch (JSONException e) {
                     e.printStackTrace();
                 }

                 new ListContent().execute();
                 new GetVersion().execute();
             } else {
                 if(sc.length == 0) {
                     // No account, return to login
                     Intent i = new Intent(e.getApplicationContext(), Login.class);
                     e.startActivity(i);
                 }
                 else {
                     // No connection
                     Toast.makeText(e, "Error reconnecting", Toast.LENGTH_SHORT).show();
                     empty.setText("Error reconnecting\nSwipe down to try again.");

                     if(newAdapter != null) {
                         newAdapter.setData(null);
                         newAdapter.notifyDataSetChanged();
                     }

                     mSwipeRefreshLayout.setRefreshing(false);
                     mSwipeRefreshLayout.setEnabled(true);
                 }
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
                    String latest = job.getString("server");
                    if (latest.length() > 0 && latest != "null") {
                        Toast.makeText(e, "Server update available: " + latest, Toast.LENGTH_SHORT).show();
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
        if(getSelectedElem().length() != 0) {
            unselectAll();
        }
        else if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new ListContent().execute();
        }
        else if(mode.equals("trash")) {
            mode = "files";
            new ListContent().execute();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        switch(item.getItemId()) {
            case 0:
                showRename(items.get(info.position).getFilename());
                return true;

            case 1:
                new Delete().execute(info.position);
                return true;

            case 2:
                showShare();
                return true;

            case 3:
                new Unshare().execute(info.position);
                return true;

            case 4:
                new Zip().execute(info.position);
                return true;

            case 5:
                download();
                return true;

            case 6:
                new Restore().execute();
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    private void download() {
        Download dl = new Download();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            dl.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            dl.execute();
        }
    }

    private void showShare() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(e);
        View shareView = View.inflate(this, R.layout.share_dialog, null);
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
                    new Share(shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked()).execute();
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
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).toString());
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

    private void showRename(final String filename) {
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
            new Rename().execute(newFilename);
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
            multipart.addFormField("target", getSelected().toString());
            multipart.addFormField("newFilename", names[0]);

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
                    .setSmallIcon(R.drawable.cloud_icon_notif)
                    .setProgress(100, 0, false);
            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected HashMap<String, String> doInBackground(String... names) {
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

            Connection multipart = new Connection("files", "read", new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    publishProgress(num);
                }
            });

            multipart.addFormField("target", getSelectedElem().toString());
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

    private JSONArray getSelectedElem() {
        JSONArray arr = new JSONArray();
        for(Item item : items) {
            if(item.isSelected()) {
                arr.put(item.getJSON());
            }
        }
        return arr;
    }

    private class Delete extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
           protected void onPreExecute() {
               super.onPreExecute();
               e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
           protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("files", "delete", null);
            multipart.addFormField("target", getSelectedElem().toString());
            multipart.addFormField("final", Boolean.toString(mode.equals("trash")));

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

    private class Share extends AsyncTask<Void, String, HashMap<String, String>> {
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
        protected HashMap<String, String> doInBackground(Void... params) {
            Connection multipart = new Connection("files", "share", null);
            multipart.addFormField("target", getSelectedElem().toString());
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

    private class Unshare extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("files", "unshare", null);
            multipart.addFormField("target", getSelected().toString());

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

    private class Zip extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("files", "zip", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).toString());
            multipart.addFormField("source", getSelectedElem().toString());

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

    private class Restore extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("files", "move", null);
            multipart.addFormField("target", hierarchy.get(0).toString());
            multipart.addFormField("trash", "true");
            multipart.addFormField("source", getSelectedElem().toString());

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
                    .setSmallIcon(R.drawable.cloud_icon_notif)
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
                upload_add_recursive(file.getParent(), file, hierarchy.get(hierarchy.size() - 1).toString());
            } else {
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", "");
                ul_elem.put("path", path);
                ul_elem.put("target", hierarchy.get(hierarchy.size() - 1).toString());
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }

        if(!uploading) {
            new Upload().execute();
        }
    }

    public void prepareNavigationDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.DrawerLayout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerLinear = (LinearLayout) findViewById(R.id.drawer);

        MenuListAdapter mMenuAdapter = new MenuListAdapter(this, titles, null, icons);
        mDrawerList.setAdapter(mMenuAdapter);
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mode = "files";
                    if(hierarchy.size() > 0) {
                        new ListContent().execute();
                    }
                    else {
                        new Connect().execute();
                    }
                } else if (position == 1) {
                    openTrash();
                } else if (position == 2) {
                    Connection.logout(e);
                    startActivity(new Intent(getApplicationContext(), org.simpledrive.Login.class));
                    finish();
                }

                mDrawerList.setItemChecked(position, true);
                mDrawerLayout.closeDrawer(mDrawerLinear);
            }
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                supportInvalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        TextView header_user = (TextView) findViewById(R.id.header_user);
        header_user.setText(username);

        TextView header_server = (TextView) findViewById(R.id.header_server);
        header_server.setText(Connection.getServer());
    }

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;

        setContentView(R.layout.activity_remotefiles);

        bPlay = (ImageButton) e.findViewById(R.id.bPlay);
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayerService.togglePlay();
                if (mPlayerService.isPlaying()) {
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

        hoverButtons = (RelativeLayout) findViewById(R.id.hover_buttons);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_menu);

            /*if (Build.VERSION.SDK_INT >= 19) {
                // Increase size of toolbar by 24dp and add padding because of translucent statusbar
                // If enabled, uncomment windowTranslucentStatus in styles.xml
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) toolbar.getLayoutParams();
                lp.height = Helper.dpToPx(80);
                toolbar.setLayoutParams(lp);
                toolbar.setPadding(0, Helper.dpToPx(24), 0, 0);
            }*/
        }

        empty = (TextView) findViewById(R.id.empty_list_item);

        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        bUpload = ((ImageButton) findViewById(R.id.bUpload));
        bCreateFolder = ((ImageButton) findViewById(R.id.bCreateFolder));
        bCreateFile = ((ImageButton) findViewById(R.id.bCreateFile));
        final ImageButton toggleButton = ((ImageButton) findViewById(R.id.bAdd));
        final ImageButton bOK = ((ImageButton) findViewById(R.id.bOK));

        bUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bCreateFolder.setVisibility(View.GONE);
                bCreateFile.setVisibility(View.GONE);
                bUpload.setVisibility(View.GONE);
                Intent result = new Intent(RemoteFiles.this, LocalFiles.class);
                startActivityForResult(result, 1);
            }
        });

        bCreateFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("folder");
            }
        });
        bCreateFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate("file");
            }
        });

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bCreateFolder.getVisibility() == View.GONE) {
                    bCreateFolder.setVisibility(View.VISIBLE);
                    bCreateFile.setVisibility(View.VISIBLE);
                    bUpload.setVisibility(View.VISIBLE);
                } else {
                    bCreateFolder.setVisibility(View.GONE);
                    bCreateFile.setVisibility(View.GONE);
                    bUpload.setVisibility(View.GONE);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toggleButton.setBackgroundResource(R.drawable.action_button_ripple);
            bUpload.setBackgroundResource(R.drawable.action_button_ripple);
            bCreateFolder.setBackgroundResource(R.drawable.action_button_ripple);
            bCreateFile.setBackgroundResource(R.drawable.action_button_ripple);
            bOK.setBackgroundResource(R.drawable.action_button_ripple);
        }

        toggleButton.setVisibility(View.VISIBLE);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loginAttempts = 0;
                if (hierarchy.size() > 0) {
                    new ListContent().execute();

                } else {
                    new Connect().execute();
                }
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Helper.dpToPx(56), Helper.dpToPx(56) + 100);

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        globLayout = (settings.getString("view", "").length() == 0) ? "list" : settings.getString("view", "");
        setView(globLayout);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (getSelectedElem().length() > 0 && !longClicked) {
                    toggleSelection(position);
                } else if (!longClicked) {
                    openFile(position);
                }
                longClicked = false;
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

        mSwipeRefreshLayout.setEnabled(true);

        // Create image cache folder
        File tmp = new File(tmpFolder);
        if (!tmp.exists()) {
            tmp.mkdir();
        }

        new Connect().execute();
    }

    protected void onPause() {
        super.onPause();
        appVisible = false;
    }

    protected void onResume() {
        super.onResume();
        appVisible = true;

        prepareNavigationDrawer();

        // Prepare audio player
        if(mPlayerService.isPlaying()) {
            showAudioPlayer();
        }
        else {
            hideAudioPlayer();
        }
    }

    public void setView(String view) {
        globLayout = view;
        settings.edit().putString("view", globLayout).apply();

        if(globLayout.equals("list")) {
            list = (ListView) findViewById(R.id.list);
            GridView tmp_grid = (GridView) findViewById(R.id.grid);
            tmp_grid.setVisibility(View.GONE);
        } else {
            list = (GridView) findViewById(R.id.grid);
            ListView tmp_list = (ListView) findViewById(R.id.list);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        if(mMenu != null) {
            invalidateOptionsMenu();
        }

        registerForContextMenu(list);
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        if(!items.get(info.position).isSelected()) {
            unselectAll();
        }
        items.get(info.position).setSelected(true);
        if(list != null) {
            if(newAdapter != null) {
                newAdapter.notifyDataSetChanged();
            }
        }

        menu.add(0, 1, 1, "Delete");

        if(!(mode.equals("trash"))) {
            if(getSelectedElem().length() == 1) {
                menu.add(0, 0, 0, "Rename");

                if(items.get(info.position).getHash().equals("null") && items.get(info.position).getOwner().equals("")) {
                    menu.add(0, 2, 2, "Share");
                } else if (!items.get(info.position).getHash().equals("null")) {
                    menu.add(0, 3, 3, "Unshare");
                }
            }

            menu.add(0, 4, 4, "Zip");
            menu.add(0, 5, 5, "Download");
        } else {
            menu.add(0, 6, 6, "Restore");
        }
        menu.setHeaderTitle("Options");
    }

    protected void onDestroy() {
        super.onDestroy();

        if (mBound && mPlayerService.isPlaying()) {
            Intent localIntent = new Intent(this, RemoteFiles.class);
            localIntent.setAction("org.simpledrive.action.startbackground");
            startService(localIntent);
        } else if (mBound) {
            getApplicationContext().unbindService(mServiceConnection);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mMenu = menu;
        if(globLayout.equals("list")) {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_grid);
        } else {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_list);
        }

        if(getSelectedElem().length() > 0) {
            menu.findItem(R.id.toggle_view).setVisible(false);
            menu.findItem(R.id.delete).setVisible(true);
            menu.findItem(R.id.download).setVisible(true);

            if(getSelectedElem().length() == 1) {
                menu.findItem(R.id.share).setVisible(true);
            } else {
                menu.findItem(R.id.share).setVisible(false);
            }
        } else {
            menu.findItem(R.id.toggle_view).setVisible(true);
            menu.findItem(R.id.delete).setVisible(false);
            menu.findItem(R.id.share).setVisible(false);
            menu.findItem(R.id.download).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case android.R.id.home:
                mDrawerLayout.openDrawer(mDrawerLinear);
                break;

            case R.id.selectall:
                selectAll();
                break;

            case R.id.logout:
                Connection.logout(e);
                startActivity(new Intent(getApplicationContext(), org.simpledrive.Login.class));
                finish();
                break;

            case R.id.clearcache:
                clearCache();
                break;

            case R.id.toggle_view:
                String next_view = (globLayout.equals("grid")) ? "list" : "grid";
                setView(next_view);
                int layout = (globLayout.equals("list")) ? R.layout.listview : R.layout.gridview;
                newAdapter = new NewFileAdapter(e, layout);
                newAdapter.setData(items);
                list.setAdapter(newAdapter);
                break;

            case R.id.delete:
                new Delete().execute();
                break;

            case R.id.download:
                download();
                break;

            case R.id.share:
                showShare();
                break;
        }

        return true;
    }

    public void clearCache() {
        File tmp = new File(tmpFolder);
        try {
            if(tmp.exists()) {
                FileUtils.cleanDirectory(tmp);
            }
        } catch (IOException exp) {
            exp.printStackTrace();
            Toast.makeText(e, "Error clearing cache", Toast.LENGTH_SHORT).show();
        }
        if(tmp.list().length == 0) {
            Toast.makeText(e, "Cache cleared", Toast.LENGTH_SHORT).show();
        }
    }

    public void openTrash() {
        if(hierarchy.size() > 0) {
            mode = "trash";
            JSONObject first = hierarchy.get(0);
            hierarchy = new ArrayList<>();
            hierarchy.add(first);
            new ListContent().execute();
        }
    }
}