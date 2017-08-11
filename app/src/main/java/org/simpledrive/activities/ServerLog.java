package org.simpledrive.activities;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.adapters.LogAdapter;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.SharedPrefManager;
import org.simpledrive.helper.Util;
import org.simpledrive.models.LogItem;

import java.util.ArrayList;

public class ServerLog extends AppCompatActivity {
    // General
    private ServerLog e;
    private int totalPages = 0;
    private int currentPage = 0;
    private ArrayList<LogItem> items = new ArrayList<>();

    private TextView page;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private TextView info;
    private AbsListView list;
    private Menu mMenu;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        int theme = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        setContentView(R.layout.activity_log);

        ImageView prev = (ImageView) findViewById(R.id.prev);
        ImageView next = (ImageView) findViewById(R.id.next);
        page = (TextView) findViewById(R.id.page);
        info = (TextView) findViewById(R.id.info);

        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage > 0) {
                    currentPage--;
                    fetchLog(currentPage);
                }
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    fetchLog(currentPage);
                }
            }
        });
        initToolbar();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
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

    private void initList() {
        list = (ListView) findViewById(R.id.list);
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

    protected void onResume() {
        super.onResume();

        if (mMenu != null) {
            invalidateOptionsMenu();
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchLog(currentPage);
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(true, Util.dpToPx(56), Util.dpToPx(56) + 100);
        mSwipeRefreshLayout.setEnabled(true);

        initList();

        fetchLog(currentPage);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mMenu.findItem(R.id.clearlog).setVisible(items.size() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.serverlog_toolbar, menu);
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
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearLog();
                            }

                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;
        }
        return true;
    }

    private void clearLog() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSwipeRefreshLayout.setRefreshing(true);
                Toast.makeText(e, "Clearing log...", Toast.LENGTH_SHORT).show();
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection multipart = new Connection("system", "clearlog");
                return multipart.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    fetchLog(currentPage);
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void fetchLog(final int page) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mSwipeRefreshLayout.setRefreshing(true);
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection multipart = new Connection("system", "log");
                multipart.addFormField("page", Integer.toString(page));

                return multipart.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                mSwipeRefreshLayout.setRefreshing(false);
                if (res.successful()) {
                    info.setVisibility(View.INVISIBLE);
                    extractLog(res.getMessage());
                    displayLog();
                }
                else {
                    info.setVisibility(View.VISIBLE);
                    info.setText(res.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractLog(String rawJSON) {
        items = new ArrayList<>();

        try {
            JSONObject j = new JSONObject(rawJSON);
            totalPages = Integer.valueOf(j.getString("total"));
            JSONArray log = j.getJSONArray("log");

            for(int i = 0; i < log.length(); i++){
                JSONObject obj = log.getJSONObject(i);

                String message = obj.getString("msg");
                String type = obj.getString("type");
                String user = obj.getString("user");
                String date = obj.getString("date");
                Bitmap icon;

                switch (type) {
                    case "info":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_info);
                        break;
                    case "warning":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_warning);
                        break;
                    default:
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_error);
                        break;
                }

                LogItem item = new LogItem(message, user, date, type, icon);
                items.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayLog() {
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

        LogAdapter newAdapter = new LogAdapter(this, R.layout.listview_detail);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
        invalidateOptionsMenu();
    }
}
