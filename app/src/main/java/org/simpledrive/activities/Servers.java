package org.simpledrive.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.adapters.UserAdapter;
import org.simpledrive.helper.UserItem;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Servers extends AppCompatActivity {
    // General
    private Servers e;
    private static ArrayList<UserItem> items = new ArrayList<>();
    private UserAdapter newAdapter;

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

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdd();
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
                                .setTitle("Delete" + getFirstSelected())
                                .setMessage("Are you sure you want to remove this server?")
                                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        remove();
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

        fetchServers();
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

    private void fetchServers() {
        for (int i = 0; i < 20; i++) {
            String username = "user" + i;
            String mode = "mode";
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_server);
            UserItem item = new UserItem(username, mode, icon);
            items.add(item);
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

    private void showAdd() {
        unselectAll();
        final android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(e);
        View shareView = View.inflate(this, R.layout.dialog_createuser, null);
        final EditText username = (EditText) shareView.findViewById(R.id.username);
        final EditText pass1 = (EditText) shareView.findViewById(R.id.pass1);
        final EditText pass2 = (EditText) shareView.findViewById(R.id.pass2);

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
                    add();
                    dialog2.dismiss();
                }
            }
        });

        username.requestFocus();
        showVirtualKeyboard();
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

    private void add() {

    }

    private void remove() {

    }
}
