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
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.adapters.UserAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.UserItem;

import java.util.ArrayList;

public class Accounts extends AppCompatActivity {
    // General
    private Accounts e;
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

        SharedPreferences settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("darktheme", "").length() == 0 || !Boolean.valueOf(settings.getString("darktheme", ""))) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        e.setTheme(theme);

        setContentView(R.layout.activity_users);

        setUpToolbar();

        info = (TextView) findViewById(R.id.info);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(getApplicationContext(), Login.class), 5);
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
            public boolean onActionItemClicked(android.view.ActionMode actionMode, final MenuItem menuItem) {
                switch (menuItem.getItemId()) {
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
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                unselectAll();
            }
        };
    }

    protected void onResume() {
        super.onResume();

        list = (ListView) findViewById(R.id.list);
        setUpList();

        getAccounts();
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

        if (mode != null) {
            mode.finish();
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
