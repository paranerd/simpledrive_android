package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
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
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
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
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.lib.AudioService;
import simpledrive.lib.AudioService.LocalBinder;
import simpledrive.lib.Connection;
import simpledrive.lib.Download.DownloadListener;
import simpledrive.lib.Helper;
import simpledrive.lib.ImageLoader;
import simpledrive.lib.MenuListAdapter;
import simpledrive.lib.NewItem;
import simpledrive.lib.Upload.ProgressListener;

public class RemoteFiles extends ActionBarActivity {
    // General
    public static RemoteFiles e;
    private static String server;
    private static String username = "";
    private static String token;

    private static String mode = "files";
    private static final String tmpFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private SharedPreferences settings;
    private boolean longClicked = false;
    private ImageLoader imgLoader;
    private static int loginAttemts;

    // Audio
    public static String audioFilename;
    private static AudioService mPlayerService;
    private boolean mBound = false;

    // Upload
    public static int uploadCurrent = 0;
    public static int uploadTotal = 0;
    public static int uploadSuccessful = 0;
    private static boolean uploading = false;
    private ArrayList<HashMap<String, String>> uploadQueue = new ArrayList<>();

    // View elements
    private static Typeface myTypeface;
    private static AbsListView list;
    private static String globLayout;
    private TextView empty;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private ImageButton bUpload;
    private ImageButton bCreate;
    private Menu mMenu;

    String titles[] = {"Files", "Trash", "Logout"};
    int icons[] = {R.drawable.ic_folder_dark, R.drawable.ic_trash_dark, R.drawable.ic_logout};
    ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;
    ListView mDrawerList;
    LinearLayout mDrawerLinear;

    // Files
    private JSONArray json = new JSONArray();
    private static ArrayList<NewItem> items = new ArrayList<>();
    private static ArrayList<JSONObject> hierarchy = new ArrayList<>();
    private Integer firstFilePos;
    private NewFileAdapter newAdapter;

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
        ProgressDialog pDialog;

    	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            empty.setText("Loading files...");
            pDialog = new ProgressDialog(RemoteFiles.this);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... args) {
    		String url = server + "api/files.php";
    		HashMap<String, String> data = new HashMap<>();

            //data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            //try {
                data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
                //data.put("path", hierarchy.get(hierarchy.size() - 1).getString("path"));
                //data.put("rootshare", hierarchy.get(hierarchy.size() - 1).getString("rootshare"));
                data.put("mode", mode);
                data.put("action", "list");
                data.put("token", token);
            /*} catch (JSONException e1) {
                e1.printStackTrace();
            }*/

            return Connection.forJSON(url, data);
    	}

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value == null) {
                Log.i("list_value", "isNull");
            }
            else {
                Log.i("list_value", value.toString());
            }
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null) {
                new Connect().execute();
            }
            else if(value.get("status").equals("ok")){
                loginAttemts = 0;
                try {
                    json = new JSONArray(value.get("msg"));
                    listContent(json);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
            pDialog.dismiss();
        }
    }

    /**
     * Extract the JSON data and call the Adapter
     * @param json Array of all files in current directory
     */
  
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void listContent(final JSONArray json) {
        // Reset anything related to listing files
        if(imgLoader != null) {
            imgLoader.cancel(true);
            imgLoader = null;
        }

        items = new ArrayList<>();
        firstFilePos = null;

        bCreate.setVisibility(View.GONE);
        bUpload.setVisibility(View.GONE);

        int thumbSize;
        if(globLayout.equals("list")) {
            thumbSize = Helper.dpToPx(40);
        } else {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            thumbSize = displaymetrics.widthPixels / 2;
        }

        // Generate ArrayList from the JSONArray
        for(int i = 0; i < json.length(); i++){
            try {
                JSONObject obj = json.getJSONObject(i);

                String filename = (mode.equals("trash")) ? obj.getString("filename").substring(0, obj.getString("filename").lastIndexOf("_trash")) : obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? "" : Helper.convertSize(obj.getString("size"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : (!obj.getString("rootshare").equals("null") ? "shared" : "");
                Bitmap thumb;

                switch (type) {
                    case "folder":
                        thumb = BitmapFactory.decodeResource(getResources(), R.drawable.folder_thumb);
                        break;
                    case "audio":
                        thumb = BitmapFactory.decodeResource(getResources(), R.drawable.audio_thumb);
                        break;
                    case "pdf":
                        thumb = BitmapFactory.decodeResource(getResources(), R.drawable.pdf_thumb);
                        break;
                    case "image":
                        String imgPath = tmpFolder + Helper.md5(parent + filename) + ".jpg";
                        String thumbPath = tmpFolder + Helper.md5(parent + filename) + "_thumb.jpg";

                        if (new File(imgPath).exists()) {
                            thumb = Helper.getThumb(imgPath, thumbSize);
                        } else if (new File(thumbPath).exists()) {
                            thumb = Helper.getThumb(thumbPath, thumbSize);
                        } else {
                            thumb = null;
                        }
                        break;
                    default:
                        thumb = BitmapFactory.decodeResource(getResources(), R.drawable.unknown_thumb);
                        break;
                }

                NewItem item = new NewItem(obj, filename, parent, size, obj.getString("edit"), type, owner, obj.getString("hash"), thumb);
                items.add(item);

                if(!type.equals("folder") && firstFilePos == null) {
                    firstFilePos = i;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        firstFilePos = (firstFilePos == null) ? items.size() : firstFilePos;

        String emptyText = (items.size() == 0) ? "Nothing to see here." : "";
        empty.setText(emptyText);

        int layout = (globLayout.equals("list")) ? R.layout.listview : R.layout.gridview;
        newAdapter = new NewFileAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);

        // Set current directory
        try {
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
                SpannableString s = new SpannableString(title);
                s.setSpan(new TypefaceSpan("fonts/robotolight.ttf"), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                toolbar.setTitle(s);
                toolbar.setSubtitle("Folders: " + firstFilePos + ", Files: " + (items.size() - firstFilePos));
            }
        } catch (JSONException e1) {
            e1.printStackTrace();
        }

        unselectAll();
   	}

    public class NewFileAdapter extends ArrayAdapter<NewItem> {
        private LayoutInflater layoutInflater;
        private NewFileAdapter e;
        private int layout;

        public NewFileAdapter(Activity mActivity, int textViewResourceId) {
            super(mActivity, textViewResourceId);
            layoutInflater = LayoutInflater.from(mActivity);
            layout = textViewResourceId;
            e = this;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            final NewItem item = getItem(position);

            if(convertView == null) {
                convertView = layoutInflater.inflate(layout, null);
                convertView.setBackgroundResource(R.drawable.bkg_light);

                holder = new ViewHolder();
                holder.thumb = (ImageView) convertView.findViewById(R.id.icon);
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

            holder.name.setTypeface(myTypeface);
            holder.size.setTypeface(myTypeface);

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());
            holder.owner.setText(item.getOwner());

            if(globLayout.equals("list")) {
                int visibility = (position == 0 || (firstFilePos != null && position == firstFilePos)) ? View.VISIBLE : View.GONE;
                holder.separator.setVisibility(visibility);

                String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
                holder.separator.setText(text);
            }

            if (!item.is("image") && globLayout.equals("grid")) {
                DisplayMetrics displaymetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                holder.thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.thumb.getLayoutParams();
                lp.height = displaymetrics.widthPixels / 8;
                lp.width = displaymetrics.widthPixels / 8;
                holder.thumb.setLayoutParams(lp);
            } else if (globLayout.equals("grid")) {
                holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) holder.thumb.getLayoutParams();
                lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                holder.thumb.setLayoutParams(lp);
            }

            int gravity = (item.is("folder") || globLayout.equals("grid")) ? Gravity.CENTER_VERTICAL : Gravity.TOP;
            holder.name.setGravity(gravity);

            if (!item.isSelected()) {
                // Item is not selected
                if(globLayout.equals("grid")) {
                    holder.name.setBackgroundColor(getResources().getColor(R.color.brightgrey));
                } else {
                    convertView.setBackgroundResource(R.drawable.bkg_light);
                }
            } else {
                // Item is selected
                if(globLayout.equals("grid")) {
                    holder.name.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                } else {
                    convertView.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                }
            }

            holder.thumb.setImageBitmap(item.getThumb());
            if(item.is("image") && item.getThumb() == null) {
                item.setThumb(BitmapFactory.decodeResource(getResources(), R.drawable.image_thumb));
                holder.thumb.setImageResource(R.drawable.image_thumb);
                String thumbPath = tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_thumb.jpg";
                loadThumb(item, thumbPath);
            }

            holder.thumb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getSelectedElem().length() > 0 || globLayout.equals("list")) {
                        toggleSelection(position);
                    } else {
                        openFile(position);
                    }
                }
            });

            holder.thumb.setOnLongClickListener(new View.OnLongClickListener() {
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
            ImageView thumb;
            TextView name;
            TextView size;
            TextView owner;
            TextView separator;
        }

        public void setData(ArrayList<NewItem> arg1) {
            clear();
            if(arg1 != null) {
                for (int i=0; i < arg1.size(); i++) {
                    add(arg1.get(i));
                }
            }
        }

        public void loadThumb(final NewItem item, String path) {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

            String size = (globLayout.equals("list")) ? Helper.dpToPx(100) + "" : Integer.toString(displaymetrics.widthPixels / 2);

            ImageLoader imgLoader = new ImageLoader(new ImageLoader.TaskListener()
            {
                @Override
                public void onFinished(final Bitmap bmp)
                {
                    if (bmp != null) {
                        // Update adapter to display thumb
                        if(list != null) {
                            item.setThumb(bmp);
                            e.notifyDataSetChanged();
                        }
                    }
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                imgLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, item.getFile().toString(), item.getFilename(), size, size, path, token);
            } else {
                imgLoader.execute(item.getFile().toString(), item.getFilename(), size, size, path, token);
            }
        }
    }

    public void openFile(int position) {
        NewItem item = items.get(position);
        if(mode == "trash") {
            return;

        } else if(item.is("folder")) {
            hierarchy.add(item.getFile());
            new ListContent().execute();

        } else if(item.is("image")) {
            Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
            i.putExtra("position", getCurrentImage(item.getFilename()));
            e.startActivity(i);

        } else if(item.is("audio") && mPlayerService != null) {
            Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
            audioFilename = item.getFilename();
            items.get(position).setSelected(true);
            new GetLink().execute(position);

        } else {
            Toast.makeText(e, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
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
        for(NewItem item : items) {
            if(item.is("image")) {
                HashMap<String, String> img = new HashMap<>();
                img.put("file", item.getFile().toString());
                img.put("filename", item.getFilename());
                img.put("path", tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + ".jpg");
                img.put("thumbPath", tmpFolder + Helper.md5(item.getParent() + item.getFilename()) + "_thumb.jpg");

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
        for(NewItem item : items) {
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
        for(NewItem item : items) {
            item.setSelected(false);
        }
        invalidateOptionsMenu();
        if(list != null) {
            newAdapter.notifyDataSetChanged();
        }
    }

    public JSONObject getSelected() {
        for(NewItem item : items) {
            if(item.isSelected()) {
                return item.getFile();
            }
        }
        return null;
    }

    public class GetLink extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... info) {
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("target", items.get(info[0]).getFile().toString());
            data.put("type",  "mobile_audio");
            data.put("filename", audioFilename);
            data.put("action", "cache");
            data.put("token", token);

            return Connection.forJSON(url, data);
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok")) {
                mPlayerService.initPlay(server + value.get("msg"));
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class Connect extends AsyncTask<String, String, HashMap<String, String>> {
        Account[] sc;

    	@Override
        protected void onPreExecute() {
            loginAttemts++;
            super.onPreExecute();
            empty.setText("Connecting ...");
    	}
    	
    	@Override
        protected HashMap<String, String> doInBackground(String... login) {
            AccountManager accMan = AccountManager.get(RemoteFiles.this);
            sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttemts > 1) {
                HashMap<String, String> map = new HashMap<>();
                map.put("status", "error");
                map.put("msg", "An error occured");
                return map;
            }

            username = sc[0].name;
            token = accMan.getUserData(sc[0], "token");

            String url = server + "api/core.php";
            HashMap<String, String> data = new HashMap<>();
            data.put("action", "login");
            data.put("token", token);
            data.put("user", username);
            data.put("pass", accMan.getPassword(sc[0]));

            Log.i("data", data.toString());

            return Connection.forJSON(url, data);
    	}
    	 @Override
         protected void onPostExecute(HashMap<String, String> value) {
             if(value.get("status").equals("ok")) {
                 try {
                     hierarchy = new ArrayList<>();

                     JSONObject currDir = new JSONObject();
                     currDir.put("path", "");
                     currDir.put("rootshare", 0);
                     hierarchy.add(currDir);

                     empty.setText("Nothing to see here.");
                     token = value.get("msg");
                 } catch (JSONException e) {
                     e.printStackTrace();
                 }

                 new ListContent().execute();
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

        } else if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new ListContent().execute();

        } else if(mode.equals("trash")) {
            mode = "files";
            new ListContent().execute();

        } else {
            super.onBackPressed();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("type", "folder");
            data.put("filename", pos[0]);
            data.put("action", "create");
            data.put("token", token);
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            return Connection.forJSON(url, data);
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

    private void showCreate() {
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("New Folder");

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newFilename = input.getText().toString();
                new NewFile().execute(newFilename);
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
            public void run()
            {
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "rename");
            data.put("token", token);
            data.put("newFilename", names[0]);
            data.put("target", getSelected().toString());
            return Connection.forJSON(url, data);
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

    private class Download extends AsyncTask<String, Integer, String> {
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
        protected String doInBackground(String... names) {
            String url = server + "api/files.php";

            HashMap<String, String> data = new HashMap<>();

            data.put("action", "download");
            data.put("token", token);
            data.put("source", getSelectedElem().toString());
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());

            new simpledrive.lib.Download(new DownloadListener()
            {
                @Override
                public void transferred(Integer num)
                {
                    if(num % 5 == 0) {
                        publishProgress(num);
                    }
                }
            });

            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            return simpledrive.lib.Download.download(url, data, path);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false);
            mNotifyManager.notify(notificationId, mBuilder.build());

            if(values[0] == 100) {
                mBuilder.setContentTitle("Download complete")
                .setOngoing(false)
                .setContentText("")
                .setProgress(0,0,false);
                mNotifyManager.notify(notificationId, mBuilder.build());
            }
        }

        @Override
        protected void onPostExecute(String value) {
            // Do something
        }
  }

    private JSONArray getSelectedElem() {
        JSONArray arr = new JSONArray();
        for(NewItem item : items) {
            if(item.isSelected()) {
                arr.put(item.getFile());
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "delete");
            data.put("token", token);
            data.put("final", Boolean.toString(mode.equals("trash")));
            data.put("source", getSelectedElem().toString());
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            return Connection.forJSON(url, data);
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("mail", "");
            data.put("key", "");
            data.put("userto", shareUser);
            data.put("pubAcc", Integer.toString(sharePublic));
            data.put("write", Integer.toString(shareWrite));
            data.put("action", "share");
            data.put("token", token);
            data.put("target", getSelected().toString());
            return Connection.forJSON(url, data);
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "unshare");
            data.put("token", token);
            data.put("target", getSelected().toString());
            return Connection.forJSON(url, data);
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
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "zip");
            data.put("token", token);
            data.put("source", getSelectedElem().toString());
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            return Connection.forJSON(url, data);
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

            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("test", "test");
            data.put("trash", "true");
            data.put("action", "move");
            data.put("token", token);
            data.put("source", getSelectedElem().toString());
            data.put("target", hierarchy.get(0).toString());

            return Connection.forJSON(url, data);
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

    private class Upload extends AsyncTask<String, Integer, String> {
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

            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(e);
            mBuilder.setContentIntent(pIntent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.cloud_icon_notif)
                    .setProgress(100, 0, false);
            mNotifyManager.notify(notificationId, mBuilder.build());

            uploadCurrent++;
            fullCurrent = ShareFiles.uploadCurrent + uploadCurrent;
            fullTotal = ShareFiles.uploadTotal + uploadTotal;
        }

        @Override
        protected String doInBackground(String... path) {
            HashMap<String, String> ul_elem = uploadQueue.remove(0);
            filename = ul_elem.get("filename");
            String filepath = ul_elem.get("path");
            String relative = ul_elem.get("relative");
            String target = ul_elem.get("target");

            String url = server + "api/files.php";

            simpledrive.lib.Upload myEntity = new simpledrive.lib.Upload(new ProgressListener() {
            @Override
            public void transferred(Integer num) {
                if(num % 5 == 0) {
                    publishProgress(num);
                }
            }
            });

            return simpledrive.lib.Upload.upload(myEntity, url, filepath, relative, target, token);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false).setContentTitle("Uploading " + fullCurrent + " of " + fullTotal).setContentText(filename);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected void onPostExecute(String value) {
            uploadSuccessful = (value.length() > 0) ? uploadSuccessful : uploadSuccessful + 1;
            fullSuccessful = ShareFiles.uploadSuccessful + uploadSuccessful;
            if(uploadQueue.size() > 0) {
                new Upload().execute();
            } else {
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

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_menu, R.string.drawer_open, R.string.drawer_close) {
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
        header_server.setText(server);
    }

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        setContentView(R.layout.activity_remotefiles);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);

            /*if (Build.VERSION.SDK_INT >= 19) {
                // Increase size of toolbar by 24dp and add padding because of translucent statusbar
                // If enabled, uncomment windowTranslucentStatus in styles.xml
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) toolbar.getLayoutParams();
                lp.height = Helper.dpToPx(80);
                toolbar.setLayoutParams(lp);
                toolbar.setPadding(0, Helper.dpToPx(24), 0, 0);
            }*/
        }

        myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
        empty = (TextView) findViewById(R.id.empty_list_item);
        empty.setTypeface(myTypeface);

        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        bUpload = ((ImageButton) findViewById(R.id.bUpload));
        bCreate = ((ImageButton) findViewById(R.id.bCreate));
        final ImageButton toggleButton = ((ImageButton) findViewById(R.id.bAdd));
        final ImageButton bOK = ((ImageButton) findViewById(R.id.bOK));

        bUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bCreate.setVisibility(View.GONE);
                bUpload.setVisibility(View.GONE);
                Intent result = new Intent(RemoteFiles.this, LocalFiles.class);
                startActivityForResult(result, 1);
            }
        });

        bCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bCreate.getVisibility() == View.GONE) {
                    bCreate.setVisibility(View.VISIBLE);
                    bUpload.setVisibility(View.VISIBLE);
                } else {
                    bCreate.setVisibility(View.GONE);
                    bUpload.setVisibility(View.GONE);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toggleButton.setBackgroundResource(R.drawable.action_button_ripple);
            bUpload.setBackgroundResource(R.drawable.action_button_ripple);
            bCreate.setBackgroundResource(R.drawable.action_button_ripple);
            bOK.setBackgroundResource(R.drawable.action_button_ripple);
        }

        toggleButton.setVisibility(View.VISIBLE);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (hierarchy.size() > 0) {
                    loginAttemts = 0;
                    new ListContent().execute();

                } else {
                    new Connect().execute();
                }
            }
        });

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Helper.dpToPx(56), Helper.dpToPx(56) + 100);

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        server = settings.getString("server", "");

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

        loginAttemts = 0;
        new Connect().execute();
    }

    protected void onResume() {
        super.onResume();

        prepareNavigationDrawer();
    }

    public void setView(String view) {
        globLayout = view;
        settings.edit().putString("view", globLayout).commit();

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

            case R.id.trash:
                openTrash();
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