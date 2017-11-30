package org.simpledrive.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.UserItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class Users extends AppCompatActivity {
    // General
    private ArrayList<UserItem> items = new ArrayList<>();
    private UserAdapter newAdapter;
    private int selectedPos;

    // Interface
    private TextView info;
    private AbsListView list;
    private FloatingActionButton fab;
    private Menu mContextMenu;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        initInterface();
        initToolbar();
    }

    protected void onResume() {
        super.onResume();

        initList();
        new FetchUsers(this).execute();
    }

    private void initInterface() {
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        setContentView(R.layout.activity_users);

        info = (TextView) findViewById(R.id.info);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    private void initList() {
        list = (ListView) findViewById(R.id.list);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                unselectAll();
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.users_context, menu);
                mContextMenu = menu;

                unselectAll();
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.delete:
                        new android.support.v7.app.AlertDialog.Builder(getApplicationContext())
                                .setTitle("Delete " + getFirstSelected())
                                .setMessage("Are you sure you want to delete this user?")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        new Delete(Users.this, getFirstSelected()).execute();
                                    }

                                })
                                .setNegativeButton("No", null)
                                .show();
                        break;
                }
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                if (selectedPos != position && checked) {
                    selectedPos = position;
                    unselectAll();
                    list.setItemChecked(position, true);
                }

                mContextMenu.findItem(R.id.delete).setVisible(true);

                newAdapter.notifyDataSetChanged();
                mode.setTitle(list.getCheckedItemCount() + " selected");
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(getApplicationContext(), UserDetails.class);
                i.putExtra("username", items.get(position).getUsername());
                unselectAll();
                startActivity(i);
            }
        });
    }

    private static class FetchUsers extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Users> ref;

        FetchUsers(Users ctx) {
            this.ref = new WeakReference<>(ctx);
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("user", "getall");

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Users act = ref.get();
            if (res.successful()) {
                act.extractFiles(res.getMessage());
                act.displayUsers();
            }
            else {
                act.setInfo(res.getMessage());
            }
        }
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

                String username = obj.getString("username");
                String mode = (obj.getString("admin").equals("1")) ? "admin" : "user";
                UserItem item = new UserItem(username, mode);
                items.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void setInfo(String msg) {
        int visibility = (msg.equals("")) ? View.GONE : View.VISIBLE;
        info.setVisibility(visibility);
        info.setText(msg);
    }

    private void displayUsers() {
        if (items.size() == 0) {
            setInfo(getResources().getString(R.string.empty));
        }
        else {
            setInfo("");
        }

        int layout = R.layout.listview_detail;
        newAdapter = new UserAdapter(this, layout, list);
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
    }

    private String getFirstSelected() {
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
        final android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(this);
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
                    Toast.makeText(getApplicationContext(), "Enter a username", Toast.LENGTH_SHORT).show();
                }
                else if (!pass1.getText().toString().equals(pass2.getText().toString())) {
                    Toast.makeText(getApplicationContext(), "Passwords don't match", Toast.LENGTH_SHORT).show();
                }
                else {
                    new Create(Users.this, username.getText().toString(), pass1.getText().toString(), admin.isChecked()).execute();
                    dialog2.dismiss();
                }
            }
        });

        username.requestFocus();
        Util.showVirtualKeyboard(this);
    }

    private static class Create extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Users> ref;
        private String username;
        private String pass;
        private boolean admin;

        Create(Users ctx, String username, String pass, boolean admin) {
            this.ref = new WeakReference<>(ctx);
            this.username = username;
            this.pass = pass;
            this.admin = admin;
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("user", "create");
            con.addFormField("user", username);
            con.addFormField("pass", pass);
            con.addFormField("admin", (admin) ? "1" : "0");
            con.addFormField("mail", "");

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Users act = ref.get();
            if (res.successful()) {
                new FetchUsers(act).execute();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Delete extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Users> ref;
        private String username;

        Delete(Users ctx, String username) {
            this.ref = new WeakReference<>(ctx);
            this.username = username;
        }

        @Override
        protected Connection.Response doInBackground(Void... names) {
            Connection con = new Connection("user", "delete");
            con.addFormField("user", username);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Users act = ref.get();
            if (res.successful()) {
                new FetchUsers(act).execute();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
