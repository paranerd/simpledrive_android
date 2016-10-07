package org.simpledrive.activities;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import org.simpledrive.R;

import java.util.HashMap;

import org.simpledrive.helper.Connection;

public class Editor extends AppCompatActivity {

    // General
    public static Editor e;

    // Editor
    private String file;
    private String filename;
    private boolean saved = true;

    // View
    private Menu mMenu;
    private Toolbar toolbar;
    private EditText editor;
    private ScrollView scroller;

    @Override
    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        e = this;

        SharedPreferences settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        int theme = (settings.getString("darktheme", "").length() == 0 || !Boolean.valueOf(settings.getString("darktheme", ""))) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        e.setTheme(theme);

        setContentView(R.layout.activity_editor);

        editor = (EditText) findViewById(R.id.editor);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        scroller = (ScrollView) findViewById(R.id.editorscroll);

        Bundle extras = getIntent().getExtras();
        file = extras.getString("file");
        filename = extras.getString("filename");

        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle("test");
            toolbar.setSubtitle("");
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    e.finish();
                }
            });
        }

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                saved = false;
                if(toolbar != null) {
                    toolbar.setTitle(filename + "*");
                    toolbar.setSubtitle("Changed...");
                }
                invalidateOptionsMenu();
            }
        });

        maximizeEditor();
        load(file);
    }

    public void onBackPressed() {
        if (!saved) {
            new AlertDialog.Builder(this)
                    .setTitle("Closing Editor")
                    .setMessage("Discard changes?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }

                    })
                    .setNegativeButton("No", null)
                    .show();
        }
        else {
            super.onBackPressed();
        }
    }

    public void maximizeEditor() {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        editor.setMinHeight(displaymetrics.heightPixels);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_toolbar, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (saved) {
            mMenu.findItem(R.id.savetext).setVisible(false);
        }
        else {
            mMenu.findItem(R.id.savetext).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.savetext:
                if(toolbar != null) {
                    toolbar.setSubtitle("Saving...");
                }
                save(file, editor.getText().toString());
                break;
        }
        return true;
    }

    private void load(final String target) {
        new AsyncTask<Void, Void, HashMap<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected HashMap<String, String> doInBackground(Void... pos) {
                Connection multipart = new Connection("files", "loadtext");
                multipart.addFormField("target", target);

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(HashMap<String, String> value) {
                e.setProgressBarIndeterminateVisibility(false);
                if(value.get("status").equals("ok")) {
                    editor.setText(value.get("msg"));
                    saved = true;
                    setToolbarTitle(filename);
                    setToolbarSubtitle("Saved.");
                }
                else {
                    Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void save(final String target, final String data) {
        new AsyncTask<Void, Void, HashMap<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                e.setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected HashMap<String, String> doInBackground(Void... pos) {
                Connection multipart = new Connection("files", "savetext");
                multipart.addFormField("target", target);
                multipart.addFormField("data", data);

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(HashMap<String, String> value) {
                e.setProgressBarIndeterminateVisibility(false);
                if(value.get("status").equals("ok")) {
                    saved = true;
                    if(toolbar != null) {
                        toolbar.setTitle(filename);
                        toolbar.setSubtitle("Saved.");
                    }
                    invalidateOptionsMenu();
                }
                else {
                    Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void setToolbarTitle(final String title) {
        if (e.getSupportActionBar() != null) {
            e.getSupportActionBar().setTitle(title);
        }
    }

    private static void setToolbarSubtitle(final String subtitle) {
        if (e.getSupportActionBar() != null) {
            e.getSupportActionBar().setSubtitle(subtitle);
        }
    }
}
