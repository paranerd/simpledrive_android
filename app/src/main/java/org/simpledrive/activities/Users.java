package org.simpledrive.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.adapters.UserAdapter;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.UserItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Users extends AppCompatActivity {
    // General
    private Users e;
    private static ArrayList<UserItem> items = new ArrayList<>();
    private UserAdapter newAdapter;

    private TextView info;
    private static AbsListView list;
    private FloatingActionButton fab;

    private ActionMode mode;
    private android.view.ActionMode.Callback modeCallBack;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;

        setContentView(R.layout.activity_users);

        setUpToolbar();

        info = (TextView) findViewById(R.id.info);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });

        modeCallBack = new android.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                mode = actionMode;
                actionMode.setTitle(getFirstSelected());
                actionMode.getMenuInflater().inflate(R.menu.users_context, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.delete:
                        new android.support.v7.app.AlertDialog.Builder(e)
                                .setTitle("Delete " + getFirstSelected())
                                .setMessage("Are you sure you want to delete this user?")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        delete(getFirstSelected());
                                    }

                                })
                                .setNegativeButton("No", null)
                                .show();
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                unselectAll();
            }
        };
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);
        setUpList();
        fetchUsers();
    }

    public void setUpToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

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

    public void setUpList() {
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                list.setItemChecked(position, true);
                startActionMode(modeCallBack);
                newAdapter.notifyDataSetChanged();
                return true;
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(e.getApplicationContext(), UserDetails.class);
                i.putExtra("username", items.get(position).getUsername());
                unselectAll();
                e.startActivity(i);
            }
        });
    }

    private void fetchUsers() {
        new AsyncTask<Void, Void, HashMap<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected HashMap<String, String> doInBackground(Void... args) {
                Connection con = new Connection("users", "get");

                return con.finish();
            }

            @Override
            protected void onPostExecute(HashMap<String, String> result) {
                if(result == null || !result.get("status").equals("ok")) {
                    String msg = (result == null) ? getResources().getString(R.string.unknown_error) : result.get("msg");
                    info.setVisibility(View.VISIBLE);
                    info.setText(msg);
                }
                else {
                    info.setVisibility(View.INVISIBLE);
                    extractFiles(result.get("msg"));
                    displayUsers();
                }
            }
        }.execute();
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractFiles(String rawJSON) {
        items = new ArrayList<>();

        try {
            JSONArray users = new JSONArray(rawJSON);

            for(int i = 0; i < users.length(); i++){
                JSONObject obj = users.getJSONObject(i);

                String username = obj.getString("user");
                String mode = (obj.getString("admin").equals("1")) ? "admin" : "user";
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account);
                UserItem item = new UserItem(username, mode, icon);
                items.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(e, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayUsers() {
        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        int layout = R.layout.userlist;
        newAdapter = new UserAdapter(e, layout, list);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
    }

    /**
     * Removes all selected Elements
     */

    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }

        if (mode != null) {
            mode.finish();
        }
        Log.i("te", "st");
    }

    public String getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return items.get(i).getUsername();
            }
        }
        return null;
    }

    private void showCreate() {
        unselectAll();
        final android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(e);
        View shareView = View.inflate(this, R.layout.dialog_createuser, null);
        final EditText username = (EditText) shareView.findViewById(R.id.username);
        final EditText pass1 = (EditText) shareView.findViewById(R.id.pass1);
        final EditText pass2 = (EditText) shareView.findViewById(R.id.pass2);
        final CheckBox admin = (CheckBox) shareView.findViewById(R.id.admin);

        dialog.setTitle("Create user")
                .setView(shareView)
                .setCancelable(true)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
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

        final android.app.AlertDialog dialog2 = dialog.create();
        dialog2.show();
        dialog2.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (username.getText().toString().isEmpty()) {
                    Toast.makeText(e, "Enter a username", Toast.LENGTH_SHORT).show();
                }
                else if (!pass1.getText().toString().equals(pass2.getText().toString())) {
                    Toast.makeText(e, "Passwords don't match", Toast.LENGTH_SHORT).show();
                }
                else {
                    create(username.getText().toString(), pass1.getText().toString(), admin.isChecked());
                    dialog2.dismiss();
                }
            }
        });

        username.requestFocus();
        showVirtualKeyboard();
    }

    private void create(final String username, final String pass, final boolean admin) {
        new AsyncTask<Void, Void, HashMap<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected HashMap<String, String> doInBackground(Void... pos) {
                String a = (admin) ? "1" : "0";
                Connection con = new Connection("users", "create");
                con.addFormField("user", username);
                con.addFormField("pass", pass);
                con.addFormField("admin", a);
                con.addFormField("mail", "");

                return con.finish();
            }

            @Override
            protected void onPostExecute(HashMap<String, String> result) {
                if (result.get("status").equals("ok")) {
                    fetchUsers();
                }
                else {
                    Toast.makeText(e, result.get("msg"), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void delete(final String username) {
        new AsyncTask<Void, Void, HashMap<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected HashMap<String, String> doInBackground(Void... params) {
                Connection con = new Connection("users", "delete");
                con.addFormField("user", username);

                return con.finish();
            }

            @Override
            protected void onPostExecute(HashMap<String, String> result) {
                if (result.get("status").equals("ok")) {
                    fetchUsers();
                }
                else {
                    Toast.makeText(e, result.get("msg"), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
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
}
