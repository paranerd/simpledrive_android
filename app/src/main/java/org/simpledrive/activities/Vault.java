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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
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
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItem;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Vault extends AppCompatActivity {
    // General
    private ArrayList<VaultItem> items = new ArrayList<>();
    private ArrayList<VaultItem> filteredItems = new ArrayList<>();
    private VaultAdapter newAdapter;
    private int selectedPos;
    private boolean waitingForUnlock = false;
    private boolean waitingForSetPassphrase = false;
    private boolean changed = false;
    private boolean createNewVault = false;
    private int unlockAttempts= 0;

    // Vault
    private ArrayList<VaultItem> vault = new ArrayList<>();
    private String currentGroup = "";
    private final String username = CustomAuthenticator.getUsername();
    private final String server = CustomAuthenticator.getServer();
    private final String salt = CustomAuthenticator.getSalt();
    private final String vaultname = Util.md5(username + server + salt);
    private String vaultEncrypted;
    private String passphrase = "";

    // Request Codes
    private static final int REQUEST_UNLOCK = 0;
    private static final int REQUEST_SET_PASSPHRASE = 1;
    private static final int REQUEST_ITEM = 2;

    // Interface
    private TextView info;
    private AbsListView list;
    private Menu mContextMenu;
    private SearchView searchView = null;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        initInterface();
        initToolbar();
    }

    protected void onResume() {
        super.onResume();

        if (!waitingForUnlock && !waitingForSetPassphrase) {
            initList();
            supportInvalidateOptionsMenu();
            load();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        items = new ArrayList<>();
        vaultEncrypted = "";
        passphrase = "";
    }

    public void onBackPressed() {
        if (!currentGroup.equals("")) {
            showGroups();
        }
        else {
            super.onBackPressed();
        }
    }

    private void initInterface() {
        // Set theme
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
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
                createEntry();
            }
        });
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

            case REQUEST_ITEM:
                if (resultCode == RESULT_OK) {
                    int id = data.getIntExtra("id", -1);
                    VaultItem item = data.getParcelableExtra("item");

                    if (item == null) {
                        item = new VaultItem();
                    }

                    saveEntry(item);
                }
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.save).setVisible(changed || !Preferences.getInstance(this).read(Preferences.TAG_VAULT_IN_SYNC, false));
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
        if (searchView != null && searchManager != null) {
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
        if (!is_unlocked()) {
            // Vault is not yet unlocked
            return false;
        }

        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.sync:
                new Sync(this, vaultEncrypted, getLastEdit()).execute();
                break;

            case R.id.save:
                save();
                break;

            case R.id.changepass:
                showChangePassword();
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
                                        deleteEntry(filteredItems.get(getFirstSelected()).getId());
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
                if (list.getAdapter() instanceof SimpleAdapter) {
                    if (list.getAdapter().getItem(position) instanceof Map) {
                        Log.i("sd_debug", "is map!");
                        Map<String, Object> group = (Map<String, Object>) list.getAdapter().getItem(position);
                        openGroup(group.get("name").toString());
                    }
                }
                else {
                    Intent i = new Intent(getApplicationContext(), VaultEntry.class);

                    i.putExtra("item", filteredItems.get(position));
                    unselectAll();
                    startActivityForResult(i, REQUEST_ITEM);
                }
            }
        });
    }

    private void load() {
        showInfo("Loading...");
        // Vault has already been loaded - just display
        if (vault.size() > 0 || createNewVault) {
            display();
        }
        // Load vault from local file
        else if (new File(getFilesDir() + "/" + vaultname).exists()) {
            vaultEncrypted = Util.readFromData(vaultname, Vault.this);
            decrypt();
        }
        // Fetch vault from server
        else {
            new Fetch(this).execute();
        }
    }

    private void decrypt() {
        String vaultDecrypted = Crypto.decryptString(vaultEncrypted, passphrase);

        if (vaultDecrypted.equals("")) {
            if (waitingForUnlock) {
                waitingForUnlock = false;
                finish();
            }
            else {
                waitingForUnlock = true;
                Intent i = new Intent(this, PasswordScreen.class);
                String error = (unlockAttempts > 0) ? "Passphrase incorrect" : "";
                i.putExtra("title", "Unlock Vault");
                i.putExtra("error", error);
                startActivityForResult(i, REQUEST_UNLOCK);
                unlockAttempts++;
            }
        }
        else {
            extractEntries(vaultDecrypted);
        }
    }

    private static class Sync extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Vault> ref;
        private String vault;
        private long lastEdit;

        Sync(Vault ctx, String vault, long lastEdit) {
            this.ref = new WeakReference<>(ctx);
            this.vault = vault;
            this.lastEdit = lastEdit;
        }

        @Override
        protected Connection.Response doInBackground(Void... args) {
            Connection con = new Connection("vault", "sync");
            con.addFormField("vault", vault);
            con.addFormField("lastedit", Long.toString(lastEdit));

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Vault act = ref.get();
            if (res.successful()) {
                Toast.makeText(act, "Sync complete.", Toast.LENGTH_SHORT).show();
                act.info.setVisibility(View.INVISIBLE);
                act.vaultEncrypted = res.getMessage();

                act.changed = true;
                act.decrypt();
            }
            else {
                act.info.setVisibility(View.VISIBLE);
                act.info.setText(res.getMessage());
            }
        }
    }

    private long getLastEdit() {
        File vaultFile = new File(getFilesDir() + "/" + vaultname);
        return (vaultFile.exists()) ? vaultFile.lastModified() / 1000 : 0;
    }

    private static class SaveToServer extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Vault> ref;
        private String vault;

        SaveToServer(Vault ctx, String vault) {
            this.ref = new WeakReference<>(ctx);
            this.vault = vault;
        }

        @Override
        protected Connection.Response doInBackground(Void... args) {
            Connection con = new Connection("vault", "save");
            con.addFormField("vault", vault);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Vault act = ref.get();
            if (res.successful()) {
                Preferences.getInstance(act).write(Preferences.TAG_VAULT_IN_SYNC, true);
                Toast.makeText(act, "Saved to server", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(act, "Error saving to server", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Fetch extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Vault> ref;

        Fetch(Vault ctx) {
            this.ref = new WeakReference<>(ctx);
        }

        @Override
        protected Connection.Response doInBackground(Void... args) {
            Connection con = new Connection("vault", "get");

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Vault act = ref.get();
            if (res.successful() && !res.getMessage().equals("")) {
                act.info.setVisibility(View.INVISIBLE);
                act.vaultEncrypted = res.getMessage();

                act.changed = true;
                act.decrypt();
            }
            else {
                act.info.setVisibility(View.VISIBLE);
                act.info.setText(res.getMessage());

                act.waitingForSetPassphrase = true;
                Intent i = new Intent(act, PasswordScreen.class);
                i.putExtra("title", "Setup Vault");
                i.putExtra("repeat", true);
                act.startActivityForResult(i, REQUEST_SET_PASSPHRASE);
            }
        }
    }

    /**
     * Extract JSON-vault and convert to ArrayList
     * @param rawJSON The raw JSON-Data from the server
     */
    private void extractEntries(String rawJSON) {
        vault = new ArrayList<>();

        try {
            JSONArray entries = new JSONArray(rawJSON);

            for (int i = 0; i < entries.length(); i++){
                JSONObject obj = entries.getJSONObject(i);

                String id = obj.getString("id");
                String title = obj.getString("title");
                String group = obj.getString("group");
                String edit = obj.getString("edit");
                String logo = obj.getString("logo");
                String url = obj.getString("url");
                String user = obj.getString("username");
                String pass = obj.getString("password");
                String note = obj.getString("note");

                VaultItem item = new VaultItem(id, title, group, edit, logo, url, user, pass, note);
                vault.add(item);
            }
        } catch (JSONException exp) {
            exp.printStackTrace();
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
        }

        resetFilter();

        if (vault.size() > 0 && changed) {
            save();
        }

        display();
    }

    private ArrayList<String> getAllGroups() {
        ArrayList<String> groups = new ArrayList<>();

        for (VaultItem item: vault) {
            if (!item.getGroup().equals("") && !groups.contains(item.getGroup())) {
                groups.add(item.getGroup());
            }
        }

        return groups;
    }

    private void resetFilter() {
        filteredItems.clear();
        filteredItems.addAll(items);
    }

    private void showInfo(String msg) {
        int visibility = (msg.equals("")) ? View.GONE : View.VISIBLE;
        info.setText(msg);
        info.setVisibility(visibility);
    }

    private void showGroups() {
        List<Map<String, Object>> groups = new ArrayList<>();
        ArrayList<String> groupNames = getAllGroups();
        int folderDrawableId = this.getResources().getIdentifier("ic_folder", "drawable", this.getPackageName());

        for (int i = 0; i < groupNames.size(); i++) {
            HashMap<String, Object> group = new HashMap<>();
            group.put("name", groupNames.get(i));
            group.put("icon", folderDrawableId);
            group.put("type", "group");
            groups.add(group);
        }

        if (groups.size() == 0) {
            showInfo(getString(R.string.empty));
        }
        else {
            showInfo("");
        }

        currentGroup = "";
        list.setAdapter(new SimpleAdapter(this, groups, R.layout.listview_detail, new String[] {"icon", "name", "type"}, new int[] {R.id.icon, R.id.title, R.id.detail1}));
    }

    private void openGroup(String title) {
        items = new ArrayList<>();

        for (VaultItem item: vault) {
            if (item.getGroup().equals(title)) {
                items.add(item);
            }
        }

        resetFilter();

        if (filteredItems.size() == 0) {
            showGroups();
            return;
        }
        else {
            showInfo("");
        }

        currentGroup = title;

        newAdapter = new VaultAdapter(this, R.layout.listview_detail, list);
        newAdapter.setData(filteredItems);
        list.setAdapter(newAdapter);
    }

    private void display() {
        if (currentGroup.equals("")) {
            showGroups();
        }
        else {
            openGroup(currentGroup);
        }
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

    private boolean is_unlocked() {
        boolean unlocked = !passphrase.equals("");
        if (!unlocked) {
            Toast.makeText(Vault.this, "Vault not loaded yet", Toast.LENGTH_SHORT).show();
        }

        return unlocked;
    }

    private void createEntry() {
        if (!is_unlocked()) {
            return;
        }

        Intent i = new Intent(getApplicationContext(), VaultEntry.class);

        startActivityForResult(i, REQUEST_ITEM);
    }

    private void deleteEntry(String id) {
        for (int i = 0; i < vault.size(); i++) {
            if (vault.get(i).getId().equals(id)) {
                vault.remove(i);
                break;
            }
        }

        resetFilter();
        save();
        display();
        invalidateOptionsMenu();
    }

    private void save() {
        changed = false;
        vaultEncrypted = Crypto.encryptString(vault.toString(), passphrase);
        boolean vaultInSync = Preferences.getInstance(this).read(Preferences.TAG_VAULT_IN_SYNC, false);

        if (vaultEncrypted != null && !vaultEncrypted.equals("")) {
            if (!vaultInSync) {
                new SaveToServer(this, vaultEncrypted).execute();
            }
            if (Util.writeToData(vaultname, vaultEncrypted, Vault.this)) {
                Toast.makeText(getApplicationContext(), "Saved.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getUniqueId() {
        boolean found = false;

        while (true) {
            String id = String.valueOf(Util.getTimestamp());

            for (VaultItem item: vault) {
                if (item.getId().equals(id)) {
                    found = true;
                }
            }

            if (!found) {
                return id;
            }
        }
    }

    private Integer getVaultPositionById(String id) {
        for (int i = 0; i < vault.size(); i++) {
            if (vault.get(i).getId().equals(id)) {
                return i;
            }
        }

        return null;
    }

    public void saveEntry(VaultItem item) {
        if (item.getTitle().equals("")) {
            return;
        }

        Integer id = getVaultPositionById(item.getId());

        if (id != null) {
            vault.set(id, item);
        }
        else {
            item.setId(getUniqueId());
            vault.add(item);
        }

        resetFilter();
        Preferences.getInstance(getApplicationContext()).write(Preferences.TAG_VAULT_IN_SYNC, false);

        display();
        save();
    }

    private void showChangePassword() {
        final android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(this);
        View shareView = View.inflate(this, R.layout.dialog_change_password, null);
        final EditText oldpass = (EditText) shareView.findViewById(R.id.oldpass);
        final EditText newpass1 = (EditText) shareView.findViewById(R.id.newpass1);
        final EditText newpass2 = (EditText) shareView.findViewById(R.id.newpass2);

        dialog.setTitle("Change Password")
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
                if (oldpass.getText().toString().isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Enter the current passphrase", Toast.LENGTH_SHORT).show();
                }
                else if (!oldpass.getText().toString().equals(passphrase)) {
                    Toast.makeText(getApplicationContext(), "Passphrase incorrect", Toast.LENGTH_SHORT).show();
                }
                else if (!newpass1.getText().toString().equals(newpass2.getText().toString())) {
                    Toast.makeText(getApplicationContext(), "New passphrases don't match", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Changing passphrase", Toast.LENGTH_SHORT).show();
                    passphrase = newpass1.getText().toString();
                    save();
                    dialog2.dismiss();
                }
            }
        });

        oldpass.requestFocus();
        Util.showVirtualKeyboard(getApplicationContext());
    }
}
