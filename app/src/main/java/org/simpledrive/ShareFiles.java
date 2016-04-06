package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.lib.Helper;
import simpledrive.lib.Item;
import simpledrive.lib.Connection;

public class ShareFiles extends ActionBarActivity {
    // General
    private static int loginAttemts = 0;
    public static ShareFiles e;
    private static String username = "";
    private SharedPreferences settings;

    // Files
    private static ArrayList<Item> items = new ArrayList<>();
    private static ArrayList<JSONObject> hierarchy = new ArrayList<>();
    private int sortOrder = 1;

    // View elements
    private static AbsListView list;
    private TextView empty;
    private static String globLayout;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;

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

        empty = (TextView) findViewById(R.id.empty_list_item);

        uploadsPending = getUploads(getIntent());

        ImageButton bUpload = ((ImageButton) findViewById(R.id.bUpload));
        ImageButton bCreate = ((ImageButton) findViewById(R.id.bCreate));
        final ImageButton toggleButton = ((ImageButton) findViewById(R.id.bAdd));
        final ImageButton bOK = ((ImageButton) findViewById(R.id.bOK));

        bCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });

        bOK.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                upload_handler(uploadsPending);
                e.finish();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            toggleButton.setBackgroundResource(R.drawable.action_button_ripple);
            bUpload.setBackgroundResource(R.drawable.action_button_ripple);
            bCreate.setBackgroundResource(R.drawable.action_button_ripple);
            bOK.setBackgroundResource(R.drawable.action_button_ripple);
        }

        bOK.setVisibility(View.VISIBLE);
        bCreate.setVisibility(View.VISIBLE);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new ListContent().execute();
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(false, Helper.dpToPx(56), Helper.dpToPx(56) + 100);

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

        toolbar = (Toolbar) findViewById(R.id.toolbar);
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
            empty.setText("Loading files...");
            pDialog = new ProgressDialog(ShareFiles.this);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("files", "list", null);
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).toString());
            multipart.addFormField("mode", "files");

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            pDialog.dismiss();
            mSwipeRefreshLayout.setRefreshing(false);
            if(value.get("status").equals("ok")) {
                loginAttemts = 0;
                displayFiles(value.get("msg"));
            }
            else {
                new Connect().execute();
            }
        }
    }

    /**
     * Extract JSONArray from server-data and convert to ArrayList
     * @param rawJSON The raw JSON-Data from the server
     */
    private void displayFiles(String rawJSON) {
        // Reset anything related to listing files
        items = new ArrayList<>();

        try {
            JSONArray jar = new JSONArray(rawJSON);

            for(int i = 0; i < jar.length(); i++){
                JSONObject obj = jar.getJSONObject(i);

                String filename = obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? "" : Helper.convertSize(obj.getString("size"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : (!obj.getString("rootshare").equals("null") ? "shared" : "");
                Bitmap thumb = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder_dark);

                if(type.equals("folder")) {
                    Item item = new Item(obj, filename, parent, null, size, obj.getString("edit"), type, owner, obj.getString("hash"), thumb);
                    items.add(item);
                }
            }

            // Show current directory in toolbar
            String title;
            JSONObject hier = hierarchy.get(hierarchy.size() - 1);
            if(hier.has("filename")) {
                title = hier.getString("filename");
            }
            else {
                title = "Homefolder";
            }

            if(toolbar != null) {
                toolbar.setTitle(title);
                toolbar.setSubtitle("Folders: " + items.size());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sortByName();

        String emptyText = (items.size() == 0) ? "Nothing to see here." : "";
        empty.setText(emptyText);

        int layout = (globLayout.equals("list")) ? R.layout.listview : R.layout.gridview;
        NewFileAdapter newAdapter = new NewFileAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
    }

    private void sortByName() {
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item item1, Item item2) {
                if (item1.is("folder") && !item2.is("folder")) {
                    return -1;
                }
                if (!item1.is("folder") && item2.is("folder")) {
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

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());
            holder.owner.setText(item.getOwner());

            if(globLayout.equals("list")) {
                int visibility = (position == 0) ? View.VISIBLE : View.GONE;
                holder.separator.setVisibility(visibility);

                String text = "Folders";
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

            if(globLayout.equals("grid")) {
                holder.name.setBackgroundColor(getResources().getColor(R.color.brightgrey));
            } else {
                convertView.setBackgroundResource(R.drawable.bkg_light);
            }

            holder.name.setGravity(Gravity.CENTER_VERTICAL);
            holder.thumb.setImageBitmap(item.getThumb());

            return convertView;
        }

        class ViewHolder {
            ImageView thumb;
            TextView name;
            TextView size;
            TextView owner;
            TextView separator;
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

    public void openFile(int position) {
        Item item = items.get(position);
        hierarchy.add(item.getJSON());
        new ListContent().execute();
    }

    public class Connect extends AsyncTask<String, String, HashMap<String, String>> {
        Account[] sc;
        AccountManager accMan = AccountManager.get(ShareFiles.this);

        @Override
        protected void onPreExecute() {
            loginAttemts++;
            super.onPreExecute();
            empty.setText("Connecting ...");
        }

        @Override
        protected HashMap<String, String> doInBackground(String... login) {
            accMan = AccountManager.get(ShareFiles.this);
            sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttemts > 2) {
                HashMap<String, String> map = new HashMap<>();
                map.put("status", "error");
                map.put("msg", "An error occured");
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
                    currDir.put("rootshare", 0);
                    hierarchy.add(currDir);

                    empty.setText("Nothing to see here.");
                    Connection.setToken(value.get("msg"));
                    accMan.setUserData(sc[0], "token", value.get("msg"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                new ListContent().execute();
            } else {
                loginAttemts = 0;
                Toast.makeText(e, "Error reconnecting", Toast.LENGTH_SHORT).show();
                empty.setText("Error reconnecting");
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
            multipart.addFormField("target", hierarchy.get(hierarchy.size() - 1).toString());
            multipart.addFormField("filename", pos[0]);
            multipart.addFormField("type", "folder");

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

    public void upload_handler(ArrayList<String> paths)
    {
        for(String path : paths)
        {
            File file = new File(path);
            if(file.isDirectory())
            {
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

    private ArrayList<String> getUploads(Intent intent)
    {
        String action = intent.getAction();
        String type = intent.getType();
        ArrayList<String> uploads = new ArrayList<>();

        if (Intent.ACTION_SEND.equals(action) && type != null)
        {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type.startsWith("image/"))
            {
                uploads.add(getRealPathFromURI(uri));
            } else {
                uploads.add(uri.getPath());
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            for(Uri uri : uris) {
                if (uri.toString().startsWith("content"))
                {
                    uploads.add(getRealPathFromURI(uri));
                } else {
                    uploads.add(uri.getPath());
                }
            }
        } else {
            return null;
        }
        return uploads;
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

        registerForContextMenu(list);
    }

    protected void onDestroy()
    {
        super.onDestroy();
    }
}