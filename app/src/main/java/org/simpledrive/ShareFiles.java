package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
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
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.lib.Connection;
import simpledrive.lib.Helper;
import simpledrive.lib.ImageLoader;
import simpledrive.lib.NewItem;
import simpledrive.lib.Upload.ProgressListener;

public class ShareFiles extends ActionBarActivity {
    // General
    private static int loginAttemts = 0;
    public static ShareFiles e;
    private static String server;
    private static String username = "";
    private SharedPreferences settings;

    // Files
    private static ArrayList<NewItem> items = new ArrayList<>();
    private static ArrayList<JSONObject> hierarchy = new ArrayList<>();

    // View elements
    private static Typeface myTypeface;
    private static AbsListView list;
    private TextView empty;
    private static String globLayout;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private ImageLoader imgLoader;

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

        myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
        empty = (TextView) findViewById(R.id.empty_list_item);
        empty.setTypeface(myTypeface);

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
        server = settings.getString("server", "");
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

    private class ListContent extends AsyncTask<String, String, JSONArray> {
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
        protected JSONArray doInBackground(String... args) {
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            data.put("mode", "0");
            data.put("action", "list");

            return Connection.forJSON(url, data);
        }

        @Override
        protected void onPostExecute(JSONArray value) {
            pDialog.dismiss();
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null) {
                new Connect().execute();
            }
            else {
                loginAttemts = 0;
                listContent(value);
            }
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

        // Generate ArrayList from the JSONArray
        for(int i = 0; i < json.length(); i++){
            try {
                JSONObject obj = json.getJSONObject(i);

                String filename = obj.getString("filename");
                String parent = obj.getString("parent");
                String type = obj.getString("type");
                String size = (obj.getString("type").equals("folder")) ? "" : Helper.convertSize(obj.getString("size"));
                String owner = (!obj.getString("owner").equals(username)) ? obj.getString("owner") : (!obj.getString("closehash").equals("null") ? "shared" : "");
                Bitmap thumb = BitmapFactory.decodeResource(getResources(), R.drawable.folder_thumb);

                if(type.equals("folder")) {
                    NewItem item = new NewItem(obj, filename, parent, size, obj.getString("edit"), type, owner, obj.getString("hash"), thumb);
                    items.add(item);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String emptyText = (items.size() == 0) ? "Nothing to see here." : "";
        empty.setText(emptyText);

        int layout = (globLayout.equals("list")) ? R.layout.listview : R.layout.gridview;
        NewFileAdapter newAdapter = new NewFileAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);

        // Set current directory
        try {
            String dir = hierarchy.get(hierarchy.size() - 1).get("filename").toString();
            String title = (dir.equals("")) ? "Homefolder" : dir;
            if(toolbar != null) {
                SpannableString s = new SpannableString(title);
                s.setSpan(new TypefaceSpan("fonts/robotolight.ttf"), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                toolbar.setTitle(s);
                toolbar.setSubtitle("Folders: " + items.size());
            }
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
    }

    public class NewFileAdapter extends ArrayAdapter<NewItem> {
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

        public void setData(ArrayList<NewItem> arg1) {
            clear();
            if(arg1 != null) {
                for (int i=0; i < arg1.size(); i++) {
                    add(arg1.get(i));
                }
            }
        }
    }

    public void openFile(int position) {
        NewItem item = items.get(position);
        hierarchy.add(item.getFile());
        new ListContent().execute();
    }

    public class Connect extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            loginAttemts++;
            super.onPreExecute();
            empty.setText("Connecting ...");
        }

        @Override
        protected String doInBackground(String... login) {
            AccountManager accMan = AccountManager.get(ShareFiles.this);
            Account[] sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0 || loginAttemts > 1) {
                return null;
            }

            username = sc[0].name;
            String url = server + "api/core.php";
            HashMap<String, String> data = new HashMap<>();
            data.put("action", "login");
            data.put("user", username);
            data.put("pass", accMan.getPassword(sc[0]));

            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            if(value != null && value.equals("1")) {
                try {
                    hierarchy = new ArrayList<>();

                    JSONObject currDir = new JSONObject();
                    currDir.put("filename", "");
                    currDir.put("parent", "");
                    currDir.put("owner", username);
                    currDir.put("hash", 0);
                    currDir.put("rootshare", 0);
                    hierarchy.add(currDir);

                    empty.setText("Nothing to see here.");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                new ListContent().execute();
            } else {
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

    private class NewFile extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected String doInBackground(String... pos) {
            String url = server + "api/files.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("type", "folder");
            data.put("filename", pos[0]);
            data.put("action", "create");
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            if(value.length() == 0) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, "Error creating folder", Toast.LENGTH_SHORT).show();
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

    private class Upload extends AsyncTask<String, Integer, String> {
        private NotificationCompat.Builder mBuilder;
        private NotificationManager mNotifyManager;
        private int notificationId = 1;
        String filename;
        private int fullCurrent;
        private int fullTotal;
        private int fullSuccessful;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Intent intent = new Intent(e, ShareFiles.class);
            PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(e);
            mBuilder.setContentIntent(pIntent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.cloud_icon_notif);
            mBuilder.setProgress(100, 0, false);
            mNotifyManager.notify(notificationId, mBuilder.build());

            uploadCurrent++;
            fullCurrent = RemoteFiles.uploadCurrent + uploadCurrent;
            fullTotal = RemoteFiles.uploadTotal + uploadTotal;
        }

        @Override
        protected String doInBackground(String... path) {
            HashMap<String, String> ul_elem = uploadQueue.remove(0);
            filename = ul_elem.get("filename");
            String filepath = ul_elem.get("path");
            String relative = ul_elem.get("relative");
            String target = ul_elem.get("target");

            String url = server + "api/files.php";

            simpledrive.lib.Upload myEntity = new simpledrive.lib.Upload(new ProgressListener()
            {
                @Override
                public void transferred(Integer num)
                {
                    if(num % 5 == 0) {
                        publishProgress(num);
                    }
                }
            });
            return simpledrive.lib.Upload.upload(myEntity, url, filepath, relative, target);
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
            fullSuccessful = RemoteFiles.uploadSuccessful + uploadSuccessful;
            if(uploadQueue.size() > 0) {
                new Upload().execute();
            }
            else {
                String file = (uploadTotal == 1) ? "file" : "files";
                mBuilder.setContentTitle("Upload complete")
                        .setContentText(fullSuccessful + " of " + fullTotal + " " + file + " added")
                        .setOngoing(false)
                        .setProgress(0, 0, false);
                mNotifyManager.notify(notificationId, mBuilder.build());
                uploading = false;
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

        registerForContextMenu(list);
    }

    protected void onDestroy()
    {
        super.onDestroy();
    }
}