package org.simpledrive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import simpledrive.lib.Util;
import simpledrive.lib.Item;

public class FileSelector extends ActionBarActivity {
    // General
    private static FileSelector act;
    private static LoadThumb thumbLoader;

    // Files
    private static NewFileAdapter mAdapter;
    private static ArrayList<String> hierarchy = new ArrayList<>();
    private static ArrayList<Integer> scrollPos = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();
    private Integer firstFilePos = null;
    private int lastSelected = -1;

    // Interface
    private boolean longClicked = false;
    private TextView info;
    private static ListView list;
    private Toolbar toolbar;
    private Menu mMenu;
    private Menu mContextMenu;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        act = this;

        setContentView(R.layout.activity_fileselector);
        list = (ListView)findViewById(R.id.locallist);
        list.setEmptyView(findViewById(R.id.local_empty_list_item));

        info = (TextView) findViewById(R.id.info);

        hierarchy.add(Environment.getExternalStorageDirectory() + "/");

        mAdapter = new NewFileAdapter(act, R.layout.listview);
        list.setAdapter(mAdapter);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private int nr = 0;

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
                nr = 0;
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
                        Toast.makeText(act, "Upload started", Toast.LENGTH_SHORT).show();
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

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.finish();
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

    public static boolean isItemSelected(int pos) {
        SparseBooleanArray checked = list.getCheckedItemPositions();
        return checked.get(pos);
    }

    private void selectAll() {
        for (int i = 0; i < items.size(); i++) {
            list.setItemChecked(i, true);
        }
    }

    public void onBackPressed() {
        if(getAllSelected().length != 0) {
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
        if(items.get(position).is("folder")) {
            for(int i = scrollPos.size() - 1; i >= hierarchy.size() - 1; i--) {
                scrollPos.remove(i);
            }

            scrollPos.add(position);

            hierarchy.add(items.get(position).getPath());
            new ListContent().execute();
        } else {
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

    /*private String[] getSelectedElem() {
        List<String> list = new ArrayList<>();
        for(Item item : items) {
            if(item.isSelected()) {
                list.add(item.getPath());
            }
        }
        return list.toArray(new String[list.size()]);
    }*/

    private class ListContent extends AsyncTask<String, String, ArrayList<Item>> {
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

            if(thumbLoader != null) {
                thumbLoader.cancel(true);
                thumbLoader = null;
            }
        }

        @Override
        protected ArrayList<Item> doInBackground(String... args) {

            String dirPath = hierarchy.get(hierarchy.size() - 1);
            File dir = new File(dirPath);

            ArrayList<Item> directories = new ArrayList<>();
            ArrayList<Item> files = new ArrayList<>();

            File[] elements = dir.listFiles();
            for (File file : elements) {
                String filename = file.getName();
                String path = file.getAbsolutePath();
                String size;
                Bitmap icon;
                String type;

                if (file.isDirectory()) {
                    size = (file.listFiles().length == 1) ? file.listFiles().length + " element" : file.listFiles().length + " elements";
                    type = "folder";
                    icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder);
                    directories.add(new Item(null, filename, null, path, size, null, type, null, null, icon, null));
                } else {
                    type = getMimeType(file);
                    switch (type) {
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
                    size = Util.convertSize(file.length() + "");
                    files.add(new Item(null, filename, null, path, size, null, type, null, null, icon, null));
                }
            }
            directories = Util.sort(directories);
            files = Util.sort(files);
            items.addAll(directories);
            items.addAll(files);
            firstFilePos = directories.size();
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<Item> value) {
            pDialog.dismiss();

            if (items.size() == 0) {
                info.setVisibility(View.VISIBLE);
                info.setText(R.string.empty);
            }
            else {
                info.setVisibility(View.GONE);
            }

            mAdapter.setData(items);
            mAdapter.notifyDataSetChanged();

            if(toolbar != null) {
                File dir = new File(hierarchy.get(hierarchy.size() - 1));
                toolbar.setTitle(dir.getName());
                toolbar.setSubtitle("Folders: " + firstFilePos + ", Files: " + (items.size() - firstFilePos));
            }

            if(hierarchy.size() <= scrollPos.size()) {
                list.setSelection(scrollPos.get(hierarchy.size() - 1));
            }
        }
    }

    public String getMimeType(File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
        if(extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String filetype = mime.getMimeTypeFromExtension(extension);
            if(filetype != null) {
                String[] content = filetype.split("/");
                if(content[0].equals("image")) {
                    return "image";
                } else if(content[0].equals("audio")) {
                    return "audio";
                }
            }
        }
        return "unknown";
    }

    public class NewFileAdapter extends ArrayAdapter<Item> {
        private LayoutInflater layoutInflater;
        private int layout;

        public NewFileAdapter(Activity mActivity, int textViewResourceId) {
            super(mActivity, textViewResourceId);
            layoutInflater = LayoutInflater.from(mActivity);
            layout = textViewResourceId;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            final Item item = getItem(position);

            if(convertView == null) {
                convertView = layoutInflater.inflate(layout, null);

                holder = new ViewHolder();
                holder.icon = (ImageView) convertView.findViewById(R.id.icon);
                holder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
                holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
                holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.separator = (TextView) convertView.findViewById(R.id.separator);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());
            holder.icon.setImageBitmap(item.getIcon());

            int visibility = (position == 0 || (firstFilePos != null && position == firstFilePos)) ? View.VISIBLE : View.GONE;
            holder.separator.setVisibility(visibility);

            String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
            holder.separator.setText(text);

            if (isItemSelected(position)) {
                holder.checked.setVisibility(View.VISIBLE);
            }
            else {
                holder.checked.setVisibility(View.INVISIBLE);
                holder.checked.setBackgroundColor(getResources().getColor(R.color.transparent));
            }

            if(item.is("image")) {
                if (item.getThumb() == null) {
                    item.setThumb(BitmapFactory.decodeResource(getResources(), R.drawable.ic_image));
                    holder.thumb.setImageResource(R.drawable.ic_image);
                    new LoadThumb().execute(position);
                }
                else {
                    holder.thumb.setImageBitmap(item.getThumb());
                }
            }

            holder.icon_area.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lastSelected = position;
                    list.setItemChecked(position, !isItemSelected(position));
                }
            });

            holder.icon_area.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return true;
                }
            });

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            ImageView thumb;
            RelativeLayout checked;
            RelativeLayout icon_area;
            TextView name;
            TextView size;
            TextView separator;
        }

        public void setData(ArrayList<Item> arg1) {
            clear();
            if(arg1 != null) {
                for (int i=0; i < arg1.size(); i++) {
                    add(arg1.get(i));
                }
            }
        }
    }

    public class LoadThumb extends AsyncTask<Integer, Bitmap, Bitmap>
    {
        private int position;

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
        }

        @Override
        protected Bitmap doInBackground(Integer... hm)
        {

            if(isCancelled()) {
                return null;
            }

            position = hm[0];
            String path = items.get(position).getPath();

            int thumbSize = Util.dpToPx(40);
            return Util.getThumb(path, thumbSize);
        }
        @Override
        protected void onPostExecute(Bitmap bmp) {
            items.get(position).setThumb(bmp);
            mAdapter.notifyDataSetChanged();
        }
    }
}