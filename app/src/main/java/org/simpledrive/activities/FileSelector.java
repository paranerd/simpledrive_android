package org.simpledrive.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.helper.Permissions;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class FileSelector extends AppCompatActivity {
    // General
    //private static FileSelector e;
    private boolean multi;
    private boolean foldersonly;
    private int selectedPos;
    private final int REQUEST_STORAGE = 6;

    // Files
    private FileAdapter mAdapter;
    private ArrayList<FileItem> hierarchy;
    private ArrayList<FileItem> items = new ArrayList<>();

    // Interface
    private boolean longClicked = false;
    private TextView info;
    private ListView list;
    private Menu mMenu;
    private Menu mContextMenu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //e = this;

        Bundle extras = getIntent().getExtras();
        multi = extras.getBoolean("multi", false);
        foldersonly = extras.getBoolean("foldersonly", false);

        initInterface();
        initList();
        initToolbar();
        checkPermission();
    }

    private void initInterface() {
        // Set theme
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set view
        setContentView(R.layout.activity_fileselector);

        list = (ListView) findViewById(R.id.listview);
        info = (TextView) findViewById(R.id.info);
    }

    public void checkPermission() {
        Permissions pm = new Permissions(this, REQUEST_STORAGE);
        pm.wantStorage();
        pm.request("Access files", "Need access to files to do that.", new Permissions.TaskListener() {
            @Override
            public void onPositive() {
                initHierarchy();
            }

            @Override
            public void onNegative() {
                Toast.makeText(getApplicationContext(), "No access to files", Toast.LENGTH_SHORT).show();
                Intent returnIntent = new Intent();
                setResult(RESULT_CANCELED, returnIntent);
                finish();
            }
        });
    }

    private void initList() {
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
                inflater.inflate(R.menu.file_selector_context, menu);
                mContextMenu = menu;

                unselectAll();
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.selectall:
                        selectAll();
                        break;

                    case R.id.select:
                        Intent i = new Intent();
                        String[] paths = getAllSelected();
                        i.putExtra("paths", paths);
                        setResult(RESULT_OK, i);
                        finish();
                        break;
                }
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                if (!multi && selectedPos != position && checked) {
                    selectedPos = position;
                    unselectAll();
                    list.setItemChecked(position, true);
                }

                if (longClicked) {
                    longClicked = false;
                    return;
                }

                mContextMenu.findItem(R.id.selectall).setVisible(items.size() > 0);
                mContextMenu.findItem(R.id.select).setVisible(list.getCheckedItemCount() > 0);

                mAdapter.notifyDataSetChanged();
                mode.setTitle(list.getCheckedItemCount() + " selected");
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openFile(position);
                longClicked = false;
            }
        });
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FileSelector.this.finish();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        initHierarchy();
                    }
                    else {
                        Intent returnIntent = new Intent();
                        setResult(RESULT_CANCELED, returnIntent);
                        finish();
                    }
                }
            break;
        }
    }
}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getAllSelected().length > 0) {
            mMenu.findItem(R.id.select).setVisible(true);
        }
        else {
            mMenu.findItem(R.id.select).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.selectall:
                selectAll();
                break;
        }
        return true;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.file_selector_toolbar, menu);
        mMenu = menu;
        return true;
    }

    private void selectAll() {
        for (int i = 0; i < items.size(); i++) {
            list.setItemChecked(i, true);
        }
    }

    public void onBackPressed() {
        if (getAllSelected().length != 0) {
            unselectAll();

        } else if (hierarchy.size() > 1) {
            hierarchy.remove(hierarchy.size() - 1);
            new FetchFiles(this, getCurrentFolder().getPath(), foldersonly).execute();

        } else {
            Intent returnIntent = new Intent();
            setResult(RESULT_CANCELED, returnIntent);
            finish();
        }
    }

    private void openFile(int position) {
        if (items.get(position).is("folder")) {
            getCurrentFolder().setScrollPos(position);
            hierarchy.add(items.get(position));
            new FetchFiles(this, getCurrentFolder().getPath(), foldersonly).execute();
        }
        else {
            Intent i = new Intent();

            String[] paths = new String[] {items.get(position).getPath()};

            i.putExtra("paths", paths);
            setResult(RESULT_OK, i);
            finish();
        }
        unselectAll();
    }

    /**
     * Removes all selected Elements
     */

    private void unselectAll() {
        for (int i = 0; i < list.getCount(); i++) {
            list.setItemChecked(i, false);
        }
    }

    private String[] getAllSelected() {
        List<String> l = new ArrayList<>();
        SparseBooleanArray checked = list.getCheckedItemPositions();

        for (int i = 0; i < list.getCount(); i++) {
            if (checked.get(i)) {
                l.add(items.get(i).getPath());
            }
        }

        return l.toArray(new String[l.size()]);
    }

    private void initHierarchy() {
        hierarchy = new ArrayList<>();
        hierarchy.add(new FileItem("", "", ""));
        hierarchy.add(new FileItem("", "storage", Environment.getExternalStorageDirectory() + "/"));
        new FetchFiles(this, getCurrentFolder().getPath(), foldersonly).execute();
    }

    private FileItem getCurrentFolder() {
        return hierarchy.get(hierarchy.size() - 1);
    }

    private static class FetchFiles extends AsyncTask<Void, Void, ArrayList<FileItem>> {
        private WeakReference<FileSelector> ref;
        private ProgressDialog pDialog;
        private String path;
        private  boolean foldersonly;
        private File[] externalFilesDirs;

        FetchFiles(FileSelector ctx, String path, boolean foldersonly) {
            this.ref = new WeakReference<>(ctx);
            this.path = path;
            this.foldersonly = foldersonly;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final FileSelector act = ref.get();
                pDialog = new ProgressDialog(act);
                pDialog.setMessage("Loading files ...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(false);
                pDialog.show();

                externalFilesDirs = act.getExternalFilesDirs(null);

                if (act.mAdapter != null) {
                    act.mAdapter.cancelThumbLoad();
                }
            }
        }

        @Override
        protected ArrayList<FileItem> doInBackground(Void... args) {
            ArrayList<FileItem> items = new ArrayList<>();
            // "root"-directories (i.e. "Internal Storage" and "SD-Card")
            //if (hierarchy.size() <= 1) {
            if (path.equals("")) {
                for (File d : externalFilesDirs) {
                    File f = d.getParentFile().getParentFile().getParentFile().getParentFile();
                    items.add(new FileItem("", f.getName(), f.getAbsolutePath(), "", "", "folder", "", false, false));
                }
            }
            else {
                File[] elements = new File(path).listFiles();
                for (File file : elements) {
                    if (!file.canRead() || (!file.isDirectory() && foldersonly)) {
                        continue;
                    }
                    String filename = file.getName();
                    String path = file.getAbsolutePath();
                    String size = (file.isDirectory()) ? ((file.listFiles().length == 1) ? file.listFiles().length + " element" : file.listFiles().length + " elements") : Util.convertSize(file.length() + "");
                    String type = (file.isDirectory()) ? "folder" : getMimeType(file);

                    items.add(new FileItem("", filename, path, size, "", type, "", false, false));
                }
            }

            return Util.sortFilesByName(items, 1);
        }

        @Override
        protected void onPostExecute(ArrayList<FileItem> items) {
            if (ref.get() == null) {
                return;
            }

            final FileSelector act = ref.get();
            pDialog.dismiss();
            act.items = items;

            act.displayFiles();
        }
    }

    private void displayFiles() {
        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        int foldersCount = countFolders();
        setToolbarTitle(hierarchy.size() == 1 ? "Select" : getCurrentFolder().getFilename());
        setToolbarSubtitle("Folders: " + foldersCount + ", Files: " + (items.size() - foldersCount));

        mAdapter = new FileAdapter(this, R.layout.listview_detail, list, true);
        mAdapter.setData(items);
        mAdapter.notifyDataSetChanged();

        list.setAdapter(mAdapter);
        list.setSelection(getCurrentFolder().getScrollPos());
    }

    private int countFolders() {
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).is("folder")) {
                count = i;
                break;
            }
        }
        return count;
    }

    private static String getMimeType(File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());

        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String filetype = mime.getMimeTypeFromExtension(extension);

            if (filetype != null) {
                String[] content = filetype.split("/");
                if (content[0].equals("image")) {
                    return "image";
                }
                else if(content[0].equals("audio")) {
                    return "audio";
                }
                else if(content[1].equals("pdf")) {
                    return "pdf";
                }
                else if(content[1].equals("zip")) {
                    return "archive";
                }
            }
        }

        return "unknown";
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