package org.simpledrive.activities;

import android.content.DialogInterface;
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
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Preferences;

import java.lang.ref.WeakReference;

public class Editor extends AppCompatActivity {
    // Editor
    private String file;
    private static String filename;
    private boolean saved = true;

    // Interface
    private Menu mMenu;
    private Toolbar toolbar;
    private EditText editor;

    @Override
    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        Bundle extras = getIntent().getExtras();
        file = extras.getString("file");
        filename = extras.getString("filename");

        initInterface();
        initToolbar();

        maximizeEditor();
        new Load(this, file).execute();
    }

    private void initToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle("test");
            toolbar.setSubtitle("");
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    private void initInterface() {
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        setContentView(R.layout.activity_editor);

        editor = (EditText) findViewById(R.id.editor);
        toolbar = (Toolbar) findViewById(R.id.toolbar);

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
                setToolbarTitle(filename + "*");
                setToolbarSubtitle("Changed...");
                invalidateOptionsMenu();
            }
        });
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
                setToolbarSubtitle("Saving...");
                new Save(this, file, editor.getText().toString()).execute();
                break;
        }
        return true;
    }

    private static class Load extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Editor> ref;
        private String target;

        Load(Editor ctx, String target) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("files", "loadtext");
            multipart.addFormField("target", target);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Editor act = ref.get();
            if (res.successful()) {
                try {
                    JSONObject job = new JSONObject(res.getMessage());
                    String filename = job.getString("filename");
                    String content = job.getString("content");

                    act.editor.setText(content);
                    act.saved = true;
                    act.setToolbarTitle(filename);
                    act.setToolbarSubtitle("Saved.");
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class Save extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Editor> ref;
        private String target;
        private String data;

        Save(Editor ctx, String target, String data) {
            this.ref = new WeakReference<>(ctx);
            this.target = target;
            this.data = data;
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("files", "savetext");
            multipart.addFormField("target", target);
            multipart.addFormField("data", data);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Editor act = ref.get();
            if (res.successful()) {
                act.saved = true;
                act.setToolbarTitle(filename);
                act.setToolbarSubtitle("Saved.");
                act.invalidateOptionsMenu();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void setToolbarSubtitle(final String subtitle) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }
}
