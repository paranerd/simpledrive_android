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
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.adapters.VaultAdapter;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItem;

import java.io.File;
import java.util.ArrayList;

public class Vault extends AppCompatActivity {
    // General
    private static Vault e;
    private static ArrayList<VaultItem> items = new ArrayList<>();
    private VaultAdapter newAdapter;
    private int selectedPos;
    private String vaultname = "vault";
    private String vaultString;

    // Interface
    private TextView info;
    private AbsListView list;
    private FloatingActionButton fab;
    private Menu mContextMenu;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        setContentView(R.layout.activity_vault);

        setUpToolbar();

        info = (TextView) findViewById(R.id.info);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                create();
            }
        });
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);
        setUpList();
        fetchVault();
    }

    private void setUpToolbar() {
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

    private void setUpList() {
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
            public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.delete:
                        new android.support.v7.app.AlertDialog.Builder(e)
                                .setTitle("Delete")
                                .setMessage("Are you sure you want to delete this entry?")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        delete(getFirstSelected());
                                        mode.finish();
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
                Intent i = new Intent(getApplicationContext(), VaultEntry.class);
                i.putExtra("title", items.get(position).getTitle());
                unselectAll();
                startActivity(i);
            }
        });
    }

    private void fetchVault() {
        File v = new File(e.getFilesDir() + "/" + vaultname);
        if (v.exists()) {
            vaultString = Util.readFromFile(vaultname, e);
            extractEntries(vaultString);
            displayEntries();
        }
        else {
            syncVault();
        }
    }

    private void syncVault() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection con = new Connection("vault", "getall");

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    info.setVisibility(View.INVISIBLE);
                    // Save vault to file
                    Util.writeToFile("vault", res.getMessage(), e);
                    vaultString = res.getMessage();
                    extractEntries(res.getMessage());
                    displayEntries();
                }
                else {
                    info.setVisibility(View.VISIBLE);
                    info.setText(res.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Extract JSON-vault and convert to ArrayList
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractEntries(String rawJSON) {
        items = new ArrayList<>();

        try {
            JSONArray entries = new JSONArray(rawJSON);

            for(int i = 0; i < entries.length(); i++){
                JSONObject obj = entries.getJSONObject(i);

                String title = obj.getString("title");
                String category = obj.getString("category");
                String type = obj.getString("type");
                String icon = obj.getString("icon");
                String edit = obj.getString("edit");
                //String user = obj.getString("user");
                //String pass = obj.getString("pass");
                String note = obj.getString("note");

                int drawableResourceId = this.getResources().getIdentifier("logo_" + icon, "drawable", this.getPackageName());
                Bitmap iconBmp = BitmapFactory.decodeResource(getResources(), drawableResourceId);
                VaultItem item = new VaultItem(title, category, type, edit, note, icon, iconBmp);
                items.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayEntries() {
        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        int layout = R.layout.listview_detail;
        newAdapter = new VaultAdapter(this, layout, list);
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

    private Integer getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return i;
            }
        }
        return null;
    }

    private void create() {
        Intent i = new Intent(getApplicationContext(), VaultEntry.class);
        i.putExtra("id", "");
        startActivity(i);
    }

    private void delete(int pos) {
        items.remove(pos);
        save();
        fetchVault();
        invalidateOptionsMenu();
    }

    private static boolean save() {
        return Util.writeToFile("vault", items.toString(), Vault.e);
    }

    public static boolean saveEntry(VaultItem item) {
        // Does the item exist?
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getTitle().equals(item.getTitle())) {
                items.set(i, item);
                found = true;
                break;
            }
        }

        if (!found) {
            items.add(item);
        }

        return save();
    }

    public static VaultItem getEntry(String title) {
        for (VaultItem item : items) {
            if (item.getTitle().equals(title)) {
                return item;
            }
        }
        return null;
    }
}
