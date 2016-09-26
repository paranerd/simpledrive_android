package org.simpledrive.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;

import java.util.ArrayList;
import java.util.HashMap;

import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Item;

public class Users extends AppCompatActivity {

    // General
    private Users e;
    private static ArrayList<Item> items = new ArrayList<>();
    private UserAdapter newAdapter;

    // Interface
    private Toolbar toolbar;
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
        toolbar = (Toolbar) findViewById(R.id.toolbar);

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
                                //new Delete().execute(getAllSelected().toString());
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
                i.putExtra("username", items.get(position).getFilename());
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
            Connection multipart = new Connection("users", "getall", null);

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
        items = new ArrayList<>();

        try {
            JSONArray users = new JSONArray(rawJSON);

            for(int i = 0; i < users.length(); i++){
                JSONObject obj = users.getJSONObject(i);

                String filename = obj.getString("user");
                String mode = (obj.getString("admin").equals("1")) ? "admin" : "user";
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account);
                Item item = new Item(obj, filename, null, null, mode, "", "user", "", icon, null);
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

        int layout = R.layout.listview;
        newAdapter = new UserAdapter(e, layout);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
        invalidateOptionsMenu();
    }

    public class UserAdapter extends ArrayAdapter<Item> {
        private LayoutInflater layoutInflater;
        private int layout;

        public UserAdapter (Activity mActivity, int textViewResourceId) {
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
                holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
                holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
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

            if (isItemSelected(position)) {
                holder.checked.setVisibility(View.VISIBLE);
            }
            else {
                holder.checked.setVisibility(View.INVISIBLE);
                holder.checked.setBackgroundColor(ContextCompat.getColor(e, R.color.transparent));
            }

            holder.icon_area.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    list.setItemChecked(position, !isItemSelected(position));
                    newAdapter.notifyDataSetChanged();
                    invalidateOptionsMenu();
                }
            });

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            TextView name;
            TextView size;
            TextView owner;
            FrameLayout icon_circle;
            RelativeLayout icon_area;
            RelativeLayout checked;
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

    /**
     * Removes all selected Elements
     */

    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    public static boolean isItemSelected(int pos) {
        SparseBooleanArray checked = list.getCheckedItemPositions();
        return checked.get(pos);
    }

    public String getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return items.get(i).getFilename();
            }
        }
        return null;
    }

    private JSONArray getAllSelected() {
        JSONArray arr = new JSONArray();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                arr.put(items.get(i).getJSON());
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
            Log.i("users", "create");
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
