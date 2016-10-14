package org.simpledrive.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.ListView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.adapters.UserAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.UserItem;

import java.util.ArrayList;

public class Accounts extends AppCompatActivity {
    // General
    private Accounts e;
    private ArrayList<UserItem> items = new ArrayList<>();
    private UserAdapter newAdapter;
    private int selectedPos;

    // Interface
    private static AbsListView list;
    private FloatingActionButton fab;
    private Menu mContextMenu;


    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        SharedPreferences settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        setContentView(R.layout.activity_users);

        setUpToolbar();

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(getApplicationContext(), Login.class), 5);
            }
        });
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);
        setUpList();

        getAccounts();
    }

    private void setUpToolbar() {
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
                                .setTitle("Remove " + getFirstSelected())
                                .setMessage("Are you sure you want to remove this account?")
                                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        remove(getFirstSelected());
                                        mode.finish();
                                    }

                                })
                                .setNegativeButton("Cancel", null)
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
    }

    private void getAccounts() {
        items = new ArrayList<>();
        ArrayList<String> accounts = CustomAuthenticator.getAllAccounts(true);

        for (int i = 0; i < accounts.size(); i++){
            String username = accounts.get(i);
            String mode = "";
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_account);
            UserItem item = new UserItem(username, mode, icon);
            items.add(item);
        }

        displayUsers();
    }

    private void displayUsers() {
        int layout = R.layout.userlist;
        newAdapter = new UserAdapter(e, layout, list);
        newAdapter.setData(items);
        list.setAdapter(newAdapter);
    }

    /**
     * Removes selection from all elements
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

    private void remove(String account) {
        boolean result = CustomAuthenticator.removeAccount(account);
        if (result) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    getAccounts();
                }
            }, 100);
        }
        else {
            Toast.makeText(e, "Error deleting account", Toast.LENGTH_SHORT).show();
        }
    }
}
