package org.simpledrive.activities;

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Crypto;
import org.simpledrive.helper.SharedPrefManager;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItem;
import org.simpledrive.models.VaultItemNote;
import org.simpledrive.models.VaultItemWebsite;

import java.io.File;
import java.util.ArrayList;

public class Vault extends AppCompatActivity {
    // General
    private ArrayList<VaultItem> items = new ArrayList<>();
    private ArrayList<VaultItem> filteredItems = new ArrayList<>();
    private VaultAdapter newAdapter;
    private int selectedPos;
    private boolean waitingForUnlock = false;
    private boolean waitingForSetPassphrase = false;
    private boolean savePending = false;
    private boolean createNewVault = false;
    private int unlockAttempts= 0;

    // Vault
    private final String username = CustomAuthenticator.getUsername();
    private final String server = CustomAuthenticator.getServer();
    private final String salt = CustomAuthenticator.getSalt();
    private final String vaultname = Util.md5(username + server + salt);
    private String vaultEncrypted;
    private String passphrase = "";

    // Request Codes
    private final int REQUEST_UNLOCK = 0;
    private final int REQUEST_SET_PASSPHRASE = 1;
    private final int REQUEST_WEBSITE= 2;
    private final int REQUEST_NOTE = 3;

    // Interface
    private TextView info;
    private AbsListView list;
    private Menu mContextMenu;
    private SearchView searchView = null;
    private boolean FAB_Status = false;
    private FloatingActionButton fab_website;
    private Animation show_fab_website;
    private Animation hide_fab_website;
    private FloatingActionButton fab_note;
    private Animation show_fab_note;
    private Animation hide_fab_note;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        initInterface();
        initToolbar();
    }

    private void initInterface() {
        // Set theme
        int theme = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set layout
        setContentView(R.layout.activity_vault);

        // Info
        info = (TextView) findViewById(R.id.info);

        // Floating action buttons
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFAB(!FAB_Status);
            }
        });

        fab_website = (FloatingActionButton) findViewById(R.id.fab_website);
        show_fab_website = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_1_show);
        hide_fab_website = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_1_hide);

        fab_note = (FloatingActionButton) findViewById(R.id.fab_note);
        show_fab_note = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_2_show);
        hide_fab_note = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_vertical_2_hide);

        fab_website.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEntry("website");
            }
        });

        fab_note.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEntry("note");
            }
        });
    }


    private void toggleFAB(boolean status) {
        if (status == FAB_Status) {
            return;
        }

        FAB_Status = status;

        // Floating Action Button 2
        Animation anim1 = (status) ? show_fab_website : hide_fab_website;
        fab_website.startAnimation(anim1);
        fab_website.setClickable(status);

        // Floating Action Button 3
        Animation anim2 = (status) ? show_fab_note: hide_fab_note;
        fab_note.startAnimation(anim2);
        fab_note.setClickable(status);
    }

    protected void onResume() {
        super.onResume();

        initList();

        if (!waitingForUnlock && !waitingForSetPassphrase) {
            load();
        }
    }

    protected void onPause() {
        super.onPause();

        toggleFAB(false);
    }

    protected void onDestroy() {
        super.onDestroy();
        items = new ArrayList<>();
        vaultEncrypted = "";
        passphrase = "";

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_UNLOCK:
                if (resultCode == RESULT_OK) {
                    passphrase = data.getStringExtra("passphrase");
                    waitingForUnlock = false;
                    decrypt();
                }
                else {
                    finish();
                }
                break;

            case REQUEST_SET_PASSPHRASE:
                if (resultCode == RESULT_OK) {
                    passphrase = data.getStringExtra("passphrase");
                    waitingForSetPassphrase = false;
                    createNewVault = true;
                    display();
                }
                else {
                    finish();
                }
                break;

            case REQUEST_WEBSITE:
                if (resultCode == RESULT_OK) {
                    int id = data.getIntExtra("id", -1);
                    VaultItemWebsite web = data.getParcelableExtra("item");
                    if (web == null) {
                        web = new VaultItemWebsite();
                    }
                    saveEntry(web, id);
                }
                break;

            case REQUEST_NOTE:
                if (resultCode == RESULT_OK) {
                    int id = data.getIntExtra("id", -1);
                    VaultItemNote note = data.getParcelableExtra("item");
                    if (note == null) {
                        note = new VaultItemNote();
                    }
                    saveEntry(note, id);
                }
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.vault_toolbar, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
        }
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

            SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String newText) {
                    filter(newText);
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    filter(query);
                    return true;
                }
            };
            searchView.setOnQueryTextListener(queryTextListener);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.sync:
                sync();
                break;

            case R.id.save:
                if (vaultEncrypted != null && !vaultEncrypted.equals("")) {
                    save();
                }
                break;
        }

        return true;
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (toolbar != null) {
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
            public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.delete:
                        new android.support.v7.app.AlertDialog.Builder(Vault.this)
                                .setTitle("Delete")
                                .setMessage("Are you sure you want to delete this entry?")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        deleteEntry(getFirstSelected());
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
                VaultItem item = filteredItems.get(position);
                Intent i;
                int requestCode;

                switch (item.getType()) {
                    case "website":
                        i = new Intent(getApplicationContext(), VaultWebsite.class);
                        requestCode = REQUEST_WEBSITE;
                        break;
                    case "note":
                        i = new Intent(getApplicationContext(), VaultNote.class);
                        requestCode = REQUEST_NOTE;
                        break;
                    default:
                        return;
                }

                i.putExtra("id", position);
                i.putExtra("item", filteredItems.get(position));
                unselectAll();
                startActivityForResult(i, requestCode);
            }
        });
    }

    private void load() {
        // Vault has already been loaded - just display
        if (items.size() > 0 || createNewVault) {
            display();
        }
        // Load vault from local file
        else if (new File(getFilesDir() + "/" + vaultname).exists()) {
            vaultEncrypted = Util.readFromData(vaultname, Vault.this);
            decrypt();
        }
        // Fetch vault from server
        else {
            fetch();
        }
    }

    private void decrypt() {
        String vaultDecrypted = Crypto.decryptString(vaultEncrypted, passphrase);

        if (vaultDecrypted == null || vaultDecrypted.equals("")) {
            if (waitingForUnlock) {
                waitingForUnlock = false;
                finish();
            }
            else {
                waitingForUnlock = true;
                Intent i = new Intent(this, PasswordScreen.class);
                String error = (unlockAttempts > 0) ? "Passphrase incorrect" : "";
                i.putExtra("error", error);
                startActivityForResult(i, REQUEST_UNLOCK);
                unlockAttempts++;
            }
        }
        else {
            extractEntries(vaultDecrypted);
        }
    }

    private void sync() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                File vaultFile = new File(getFilesDir() + "/" + vaultname);
                long lastEdit = (vaultFile.exists()) ? vaultFile.lastModified() / 1000 : 0;

                Connection con = new Connection("vault", "sync");
                con.addFormField("vault", vaultEncrypted);
                con.addFormField("lastedit", Long.toString(lastEdit));

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    Toast.makeText(Vault.this, "Sync complete.", Toast.LENGTH_SHORT).show();
                    info.setVisibility(View.INVISIBLE);
                    vaultEncrypted = res.getMessage();

                    savePending = true;
                    decrypt();
                }
                else {
                    info.setVisibility(View.VISIBLE);
                    info.setText(res.getMessage());
                }
            }
        }.execute();
    }

    private void saveToServer() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection con = new Connection("vault", "save");
                con.addFormField("vault", vaultEncrypted);

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    Toast.makeText(Vault.this, "Saved to server", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(Vault.this, "Error saving to server", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void fetch() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... args) {
                Connection con = new Connection("vault", "get");

                return con.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful() && !res.getMessage().equals("")) {
                    info.setVisibility(View.INVISIBLE);
                    vaultEncrypted = res.getMessage();

                    savePending = true;
                    decrypt();
                }
                else {
                    info.setVisibility(View.VISIBLE);
                    info.setText(res.getMessage());

                    waitingForSetPassphrase = true;
                    Intent i = new Intent(getApplicationContext(), PasswordScreen.class);
                    i.putExtra("repeat", true);
                    startActivityForResult(i, REQUEST_SET_PASSPHRASE);
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

            for (int i = 0; i < entries.length(); i++){
                JSONObject obj = entries.getJSONObject(i);

                String title = obj.getString("title");
                String category = obj.getString("category");
                String type = obj.getString("type");
                String edit = obj.getString("edit");
                String logo = obj.getString("logo");

                Log.i("debug", "edit: " + edit);

                switch (type) {
                    case "website":
                        String url = obj.getString("url");
                        String user = obj.getString("user");
                        String pass = obj.getString("pass");

                        VaultItemWebsite website = new VaultItemWebsite(title, category, type, url, user, pass, edit, logo);
                        items.add(website);
                        break;

                    case "note":
                        String content = obj.getString("content");
                        VaultItemNote note = new VaultItemNote(title, category, content, edit, logo);
                        items.add(note);
                        break;
                }
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }

        filteredItems = cloneArrayList(items);

        if (filteredItems.size() > 0 && savePending) {
            save();
        }

        display();
    }

    private void display() {
        if (filteredItems.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        newAdapter = new VaultAdapter(this, R.layout.listview_detail, list);
        newAdapter.setData(filteredItems);
        list.setAdapter(newAdapter);
    }

    private void filter(String needle) {
        if (items.size() > 0) {
            filteredItems = new ArrayList<>();

            for (VaultItem item : items) {
                if (item.getTitle().toLowerCase().contains(needle)) {
                    filteredItems.add(item);
                }
            }
            display();
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

    private int getFirstSelected() {
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                return i;
            }
        }
        return -1;
    }

    private void createEntry(String type) {
        Intent i;
        int requestCode;
        switch (type) {
            case "website":
                requestCode = REQUEST_WEBSITE;
                i = new Intent(getApplicationContext(), VaultWebsite.class);
                break;

            case "note":
                requestCode = REQUEST_NOTE;
                i = new Intent(getApplicationContext(), VaultNote.class);
                break;

            default:
                return;
        }

        startActivityForResult(i, requestCode);
    }

    private void deleteEntry(int pos) {
        items.remove(pos);
        filteredItems = cloneArrayList(items);
        save();
        display();
        invalidateOptionsMenu();
    }

    private boolean save() {
        savePending = false;
        vaultEncrypted = Crypto.encryptString(items.toString(), passphrase);

        if (vaultEncrypted != null && !vaultEncrypted.equals("")) {
            saveToServer();
            return Util.writeToData(vaultname, vaultEncrypted, Vault.this);
        }

        return false;
    }

    public boolean saveEntry(VaultItem item, int id) {
        if (item.getTitle().equals("")) {
            return false;
        }
        if (id < 0 || id > items.size() - 1) {
            items.add(item);
        }
        else {
            items.set(id, item);
        }

        filteredItems = cloneArrayList(items);

        display();
        return save();
    }

    private ArrayList<VaultItem> cloneArrayList(ArrayList<VaultItem> src) {
        ArrayList<VaultItem> newList = new ArrayList<>();

        for (int i = 0; i < src.size(); i++) {
            newList.add(src.get(i));
        }

        return newList;
    }
}
