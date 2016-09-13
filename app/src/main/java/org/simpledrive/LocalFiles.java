package org.simpledrive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import simpledrive.lib.Helper;
import simpledrive.lib.Item;

public class LocalFiles extends ActionBarActivity {
    // General
    private static LocalFiles act;
    private static LoadThumb thumbLoader;

    // Files
    private static NewFileAdapter mAdapter;
    private static ArrayList<String> hierarchy = new ArrayList<>();
    private static ArrayList<Integer> scrollPos = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();
    private Integer firstFilePos = null;

    // View elements
    private boolean longClicked = false;
    private TextView empty;
    private static ListView list;
    private Toolbar toolbar;
    private Menu mMenu;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        act = this;

        setContentView(R.layout.activity_localfiles);
        list = (ListView)findViewById(R.id.locallist);
        list.setEmptyView(findViewById(R.id.local_empty_list_item));

        registerForContextMenu(list);

        empty = (TextView) findViewById(R.id.local_empty_list_item);

        hierarchy.add(Environment.getExternalStorageDirectory() + "/");

        mAdapter = new NewFileAdapter(act, R.layout.listview);
        list.setAdapter(mAdapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (getSelectedElem().length > 0 && !longClicked) {
                    toggleSelection(position);
                } else if (!longClicked) {
                    openFile(position);
                }
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        if(!items.get(info.position).isSelected()) {
            unselectAll();
        }
        items.get(info.position).setSelected(true);
        if(list != null) {
            if(mAdapter!= null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        menu.add(Menu.NONE, 5, 0, "Upload");
        menu.setHeaderTitle("Options");
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getSelectedElem().length > 0) {
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

            case R.id.select:
                Toast.makeText(act, "Upload started", Toast.LENGTH_SHORT).show();
                Intent i = new Intent();
                String[] paths = getSelectedElem();
                i.putExtra("paths", paths);
                setResult(RESULT_OK, i);
                finish();
                break;
        }
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case 5:
                Toast.makeText(act, "Upload started", Toast.LENGTH_SHORT).show();
                Intent i = new Intent();
                String[] paths = getSelectedElem();
                i.putExtra("paths", paths);
                setResult(RESULT_OK, i);
                finish();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.file_selector, menu);
        mMenu = menu;
        return true;
    }

    public void onBackPressed() {
        if(getSelectedElem().length != 0) {
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
            for(int x = 0; x < paths.length; x++) {
                Log.i("paths", items.get(x).getFilename());
            }

            if(paths.length == 0) {
                Log.i("PATHS", "EMPTY");
            }

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
        for(Item item : items) {
            item.setSelected(false);
        }
        invalidateOptionsMenu();
        if(list != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private String[] getSelectedElem() {
        List<String> list = new ArrayList<>();
        for(Item item : items) {
            if(item.isSelected()) {
                list.add(item.getPath());
            }
        }
        return list.toArray(new String[list.size()]);
    }

    public void toggleSelection(int position) {
        items.get(position).toggleSelection();

        invalidateOptionsMenu();

        if(list != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private class ListContent extends AsyncTask<String, String, ArrayList<Item>> {
        ProgressDialog pDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            empty.setText("Loading files...");
            pDialog = new ProgressDialog(LocalFiles.this);
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
                Bitmap thumb;
                String type;

                if (file.isDirectory()) {
                    size = "";
                    type = "folder";
                    thumb = BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder_dark);
                    directories.add(new Item(null, filename, null, path, size, null, type, null, null, thumb));
                } else {
                    type = getMimeType(file);
                    switch (type) {
                        case "image":
                            thumb = null;
                            break;
                        case "audio":
                            thumb = BitmapFactory.decodeResource(getResources(), R.drawable.ic_audio);
                            break;
                        case "pdf":
                            thumb = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pdf);
                            break;
                        default:
                            thumb = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unknown);
                    }
                    size = Helper.convertSize(file.length() + "");
                    files.add(new Item(null, filename, null, path, size, null, type, null, null, thumb));
                }
            }
            directories = Helper.sort(directories);
            files = Helper.sort(files);
            items.addAll(directories);
            items.addAll(files);
            firstFilePos = directories.size();
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<Item> value) {
            pDialog.dismiss();
            String emptyText = (items.size() == 0) ? "Nothing to see here." : "";
            empty.setText(emptyText);

            mAdapter.setData(items);
            mAdapter.notifyDataSetChanged();

            if(toolbar != null) {
                File dir = new File(hierarchy.get(hierarchy.size() - 1));
                //SpannableString s = new SpannableString(dir.getName());
                //s.setSpan(new TypefaceSpan("fonts/robotolight.ttf"), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                convertView.setBackgroundResource(R.drawable.bkg_light);

                holder = new ViewHolder();
                holder.thumb = (ImageView) convertView.findViewById(R.id.icon);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.separator = (TextView) convertView.findViewById(R.id.separator);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
                convertView.setBackgroundResource(R.drawable.bkg_light);
            }

            holder.name.setText(item.getFilename());
            holder.size.setText(item.getSize());

            int visibility = (position == 0 || (firstFilePos != null && position == firstFilePos)) ? View.VISIBLE : View.GONE;
            holder.separator.setVisibility(visibility);

            String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
            holder.separator.setText(text);

            int gravity = (item.is("folder")) ? Gravity.CENTER_VERTICAL : Gravity.TOP;
            holder.name.setGravity(gravity);

            if (!item.isSelected()) {
                // Item is not selected
                convertView.setBackgroundResource(R.drawable.bkg_light);
            } else {
                // Item is selected
                convertView.setBackgroundColor(getResources().getColor(R.color.lightgreen));
            }

            holder.thumb.setImageBitmap(item.getThumb());
            if(item.is("image") && item.getThumb() == null) {
                item.setThumb(BitmapFactory.decodeResource(getResources(), R.drawable.ic_image));
                holder.thumb.setImageResource(R.drawable.ic_image);
                /*if(!called) {
                    called = true;
                    new LoadThumb().execute(position);
                }*/
                new LoadThumb().execute(position);
            }

            holder.thumb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSelection(position);
                }
            });

            holder.thumb.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    longClicked = true;
                    toggleSelection(position);
                    return true;
                }
            });
            return convertView;
        }

        class ViewHolder {
            ImageView thumb;
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

            int thumbSize = Helper.dpToPx(40);
            return Helper.getThumb(path, thumbSize);
        }
        @Override
        protected void onPostExecute(Bitmap bmp) {
            items.get(position).setThumb(bmp);
            mAdapter.notifyDataSetChanged();
        }
    }
}