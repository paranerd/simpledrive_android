package org.simpledrive.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.adapters.AccountAdapter;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.models.AccountItem;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Accounts extends AppCompatActivity {
    // General
    private Accounts e;
    private ArrayList<AccountItem> items = new ArrayList<>();
    private AccountAdapter newAdapter;
    private int selectedPos;

    // Interface
    private AbsListView list;
    private FloatingActionButton fab;
    private Menu mContextMenu;


    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        SharedPreferences settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        setContentView(R.layout.activity_users);

        initToolbar();

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
                inflater.inflate(R.menu.accounts_context, menu);
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

                    case R.id.rename:
                        showRename(mode);
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

    private void showRename(final ActionMode mode) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Rename account");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                rename(getFirstSelected(), input.getText().toString());
                mode.finish();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mode.finish();
            }
        });

        alert.show();
        input.requestFocus();
        input.selectAll();
        showVirtualKeyboard();
    }

    private void showVirtualKeyboard() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                if (m != null) {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);
    }

    private void getAccounts() {
        items = CustomAuthenticator.getAllAccounts(true);

        displayAccounts();
    }

    private void displayAccounts() {
        int layout = R.layout.listview_detail;
        newAdapter = new AccountAdapter(e, layout, list);
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
                return items.get(i).getName();
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

    private void rename(String accountName, String nickname) {
        boolean result = CustomAuthenticator.setNickname(accountName, nickname);
        if (result) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    getAccounts();
                }
            }, 100);
        }
        else {
            Toast.makeText(e, "Error renaming account", Toast.LENGTH_SHORT).show();
        }
    }
}
