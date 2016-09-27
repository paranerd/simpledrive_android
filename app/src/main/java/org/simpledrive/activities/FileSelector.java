package org.simpledrive.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.simpledrive.R;
import org.simpledrive.adapters.FileAdapter;
import org.simpledrive.helper.Util;
import org.simpledrive.helper.FileItem;

public class FileSelector extends AppCompatActivity {
    // General
    private static FileSelector e;

    // Files
    private static FileAdapter mAdapter;
    private static ArrayList<FileItem> hierarchy;
    private ArrayList<FileItem> items = new ArrayList<>();
    private Integer firstFilePos = null;
    private int lastSelected = -1;

    // Interface
    private boolean longClicked = false;
    private TextView info;
    private static ListView list;
    private Menu mMenu;
    private Menu mContextMenu;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;

        setContentView(R.layout.activity_fileselector);
        list = (ListView) findViewById(R.id.listview);

        info = (TextView) findViewById(R.id.info);

        hierarchy = new ArrayList<>();
        FileItem root = new FileItem(null, "root", Environment.getExternalStorageDirectory() + "/");
        hierarchy.add(root);

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
                        Toast.makeText(e, "Upload started", Toast.LENGTH_SHORT).show();
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
                if (longClicked) {
                    longClicked = false;
                    return;
                }

                mContextMenu.findItem(R.id.selectall).setVisible(items.size() > 0);
                mContextMenu.findItem(R.id.select).setVisible(list.getCheckedItemCount() > 0);

                lastSelected = position;
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

        new ListContent().execute();
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
            new ListContent().execute();

        } else {
            Intent returnIntent = new Intent();
            setResult(RESULT_CANCELED, returnIntent);
            finish();
        }
    }

    public void openFile(int position) {
        if (items.get(position).is("folder")) {
            hierarchy.get(hierarchy.size() - 1).setScrollPos(position);
            hierarchy.add(items.get(position));
            new ListContent().execute();
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

    private class ListContent extends AsyncTask<String, String, ArrayList<FileItem>> {
        ProgressDialog pDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(FileSelector.this);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();

            firstFilePos = null;
            items = new ArrayList<>();

            if (mAdapter != null) {
                mAdapter.cancelThumbLoad();
            }
        }

        @Override
        protected ArrayList<FileItem> doInBackground(String... args) {
            String dirPath = hierarchy.get(hierarchy.size() - 1).getPath();
            File dir = new File(dirPath);

            File[] elements = dir.listFiles();
            for (File file : elements) {
                String filename = file.getName();
                String path = file.getAbsolutePath();
                String size = (file.isDirectory()) ? ((file.listFiles().length == 1) ? file.listFiles().length + " element" : file.listFiles().length + " elements") : Util.convertSize(file.length() + "");
                Bitmap icon;
                String type = (file.isDirectory()) ? "folder" : getMimeType(file);

                switch (type) {
                    case "folder":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);
                        break;
                    case "image":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_image);
                        break;
                    case "audio":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_audio);
                        break;
                    case "pdf":
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pdf);
                        break;
                    default:
                        icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unknown);
                }

                items.add(new FileItem(null, filename, null, path, size, null, type, null, icon, null, "", ""));
            }

            Util.sortFilesByName(items, 1);

            firstFilePos = items.size();

            for(int i = 0; i < items.size(); i++) {
                if (!items.get(i).is("folder")) {
                    firstFilePos = i;
                    break;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<FileItem> value) {
            pDialog.dismiss();

            displayFiles();
        }
    }

    public void displayFiles() {
        if (items.size() == 0) {
            info.setVisibility(View.VISIBLE);
            info.setText(R.string.empty);
        }
        else {
            info.setVisibility(View.GONE);
        }

        setToolbarTitle(hierarchy.get(hierarchy.size() - 1).getFilename());
        setToolbarSubtitle("Folders: " + firstFilePos + ", Files: " + (items.size() - firstFilePos));

        mAdapter = new FileAdapter(e, R.layout.filelist, list, 0, true, firstFilePos);
        mAdapter.setData(items);
        mAdapter.notifyDataSetChanged();

        list.setAdapter(mAdapter);
        list.setSelection(hierarchy.get(hierarchy.size() - 1).getScrollPos());
    }

    public String getMimeType(File file) {
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