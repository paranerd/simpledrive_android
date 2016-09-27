package org.simpledrive.activities;

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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
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

public class Users extends AppCompatActivity {

    // General
    private Users e;
    private static ArrayList<UserItem> items = new ArrayList<>();
    private UserAdapter newAdapter;

    private TextView info;
    private static AbsListView list;
    private Menu mToolbarMenu;
    private FloatingActionButton fab;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        e = this;

        setContentView(R.layout.activity_users);

        info = (TextView) findViewById(R.id.info);
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

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreate();
            }
        });
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);
        setUpList();

        if(mToolbarMenu != null) {
            invalidateOptionsMenu();
        }

        new FetchUsers().execute();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mToolbarMenu = menu;

        menu.findItem(R.id.delete).setVisible(getAllSelected().length() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.users_toolbar, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case android.R.id.home:
                //mDrawerLayout.openDrawer(GravityCompat.START);
                break;

            case R.id.delete:
                new android.support.v7.app.AlertDialog.Builder(this)
                        .setTitle("Delete")
                        .setMessage("Are you sure you want to delete this user?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new Delete(getFirstSelected()).execute();
                            }

                        })
                        .setNegativeButton("No", null)
                        .show();
                break;
        }

        return true;
    }

    public void setUpList() {
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                list.setItemChecked(position, true);
                newAdapter.notifyDataSetChanged();
                invalidateOptionsMenu();
                return true;
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(e.getApplicationContext(), UserDetails.class);
                i.putExtra("username", items.get(position).getUsername());
                e.startActivity(i);
                unselectAll();
            }
        });
    }

    private class FetchUsers extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected HashMap<String, String> doInBackground(String... args) {
            Connection multipart = new Connection("users", "get", null);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value == null || !value.get("status").equals("ok")) {
                String msg = (value == null) ? getResources().getString(R.string.unknown_error) : value.get("msg");
                info.setVisibility(View.VISIBLE);
                info.setText(msg);
            }
            else {
                info.setVisibility(View.INVISIBLE);
                extractFiles(value.get("msg"));
                displayUsers();
            }
        }
    }

    /**
     * Extract JSONArray from server-data, convert to ArrayList and display
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractFiles(String rawJSON) {
        Log.i("json", rawJSON);
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
        invalidateOptionsMenu();
    }

    /**
     * Removes all selected Elements
     */

    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
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

    private JSONArray getAllSelected() {
        JSONArray arr = new JSONArray();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                arr.put(items.get(i).getUsername());
            }
        }

        return arr;
    }

    private void showCreate() {
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
                    new Create(username.getText().toString(), pass1.getText().toString(), admin.isChecked()).execute();
                    dialog2.dismiss();
                }
            }
        });

        username.requestFocus();
        //showVirtualKeyboard();
    }

    private class Create extends AsyncTask<String, String, HashMap<String, String>> {
        private String username;
        private String pass;
        private int admin;

        public Create(String username, String pass, boolean admin) {
            Log.i(username, pass);
            this.username = username;
            this.pass = pass;
            this.admin = (admin) ? 1 : 0;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... pos) {
            Connection multipart = new Connection("users", "create", null);
            multipart.addFormField("user", username);
            multipart.addFormField("pass", pass);
            multipart.addFormField("admin", Integer.toString(this.admin));
            multipart.addFormField("mail", "");

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok")) {
                new FetchUsers().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Delete extends AsyncTask<String, String, HashMap<String, String>> {
        private String username;

        public Delete(String username) {
            this.username = username;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... pos) {
            Connection multipart = new Connection("users", "delete", null);
            multipart.addFormField("user", username);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(value.get("status").equals("ok")) {
                new FetchUsers().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
