package org.simpledrive.settings;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import simpledrive.lib.Connection;
import simpledrive.lib.Util;
import simpledrive.lib.Item;

public class ServerLog extends ActionBarActivity {

    // General
    private ServerLog e;
    private int totalPages = 0;
    private int currentPage = 0;
    private static ArrayList<Item> items = new ArrayList<>();
    private NewFileAdapter newAdapter;
    private int sortOrder = 1;
    private JSONArray log;

    // Interface
    private ImageView prev;
    private ImageView next;
    private TextView page;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private TextView info;
    private static AbsListView list;
    private Menu mMenu;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;

        setContentView(R.layout.activity_log);

        prev = (ImageView) findViewById(R.id.prev);
        next = (ImageView) findViewById(R.id.next);
        page = (TextView) findViewById(R.id.page);
        info = (TextView) findViewById(R.id.info);
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage > 0) {
                    currentPage--;
                    new FetchLog().execute();
                }
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    new FetchLog().execute();
                }
            }
        });

        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    e.finish();
                }
            });
        }
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);

        if(mMenu != null) {
            invalidateOptionsMenu();
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new FetchLog().execute();
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

        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Util.dpToPx(56), Util.dpToPx(56) + 100);

        mSwipeRefreshLayout.setEnabled(true);

        new FetchLog().execute();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (items.size() > 0) {
            mMenu.findItem(R.id.clearlog).setVisible(true);
        }
        else {
            mMenu.findItem(R.id.clearlog).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.serverlog, menu);
        mMenu = menu;
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.clearlog:
                new AlertDialog.Builder(this)
                        .setTitle("Clear log")
                        .setMessage("Are you sure you want to delete all log entries?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new ClearLog().execute();
                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
                break;
        }
        return true;
    }

    private class ClearLog extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Toast.makeText(e, "Clearing log...", Toast.LENGTH_SHORT).show();
            mSwipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("system", "clearlog", null);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null || !value.get("status").equals("ok")) {
                String msg = (value == null) ? getResources().getString(R.string.unknown_error) : value.get("msg");
                Toast.makeText(e, msg, Toast.LENGTH_SHORT).show();
            }
            else {
                new FetchLog().execute();
            }
        }
    }

    private class FetchLog extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mSwipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("system", "log", null);
            multipart.addFormField("page", Integer.toString(currentPage));

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            mSwipeRefreshLayout.setRefreshing(false);
            if(value == null || !value.get("status").equals("ok")) {
                String msg = (value == null) ? getResources().getString(R.string.unknown_error) : value.get("msg");
                info.setVisibility(View.VISIBLE);
                info.setText(msg);
            }
            else {
                info.setVisibility(View.INVISIBLE);
                displayLog(value.get("msg"));
            }
        }
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void displayLog(String rawJSON) {
        items = new ArrayList<>();

        try {
            JSONObject j = new JSONObject(rawJSON);
            totalPages = Integer.valueOf(j.getString("total"));
            log = j.getJSONArray("log");

            for(int i = 0; i < log.length(); i++){
                JSONObject obj = log.getJSONObject(i);

                String filename = obj.getString("msg");
                String type = obj.getString("type");
                String size = obj.getString("user");
                String owner = obj.getString("date");
                Bitmap icon;

                switch (type) {
                    case "0":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_info);
                        break;
                    case "1":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_warning);
                        break;
                    default:
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_error);
                        break;
                }

                Item item = new Item(obj, filename, null, null, size, obj.getString("date"), type, owner, null, icon, null);
                items.add(item);
            }

            sortByName();
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(e, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }

        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            page.setVisibility(View.INVISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
            page.setVisibility(View.VISIBLE);
            page.setText((currentPage + 1) + " / " + totalPages);
        }

        int layout = R.layout.listview;
        newAdapter = new NewFileAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
        invalidateOptionsMenu();
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

                holder = new ViewHolder();
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.icon_circle = (FrameLayout) convertView.findViewById(R.id.icon_circle);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.owner = (TextView) convertView.findViewById(R.id.owner);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());
            holder.owner.setText(item.getOwner());
            holder.icon.setImageBitmap(item.getIcon());

            int color;

            if (item.is("0")) {
                color = R.color.darkgreen;
            }
            else if (item.is("1")) {
                color = R.color.orange;
            }
            else {
                color = R.color.red;
            }

            Drawable drawable = ContextCompat.getDrawable(ServerLog.this, R.drawable.circle_drawable);
            drawable.setColorFilter(getResources().getColor(color), PorterDuff.Mode.SRC_ATOP);

            holder.icon_circle.setBackground(drawable);

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            TextView name;
            TextView size;
            TextView owner;
            FrameLayout icon_circle;
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
}
