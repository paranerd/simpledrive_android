package org.simpledrive.adapters;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

public class FileAdapter extends ArrayAdapter<FileItem> implements Serializable {
    private LayoutInflater layoutInflater;
    private int layout;
    private boolean loadthumbs = false;
    private ArrayList<FileItem> thumbQueue = new ArrayList<>();
    private boolean thumbLoading = false;
    private AppCompatActivity e;
    private AbsListView list;
    private Integer thumbSize;

    public FileAdapter(AppCompatActivity mActivity, int layoutResourceId, AbsListView list, boolean loadthumbs) {
        super(mActivity, layoutResourceId);
        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = layoutResourceId;
        this.loadthumbs = loadthumbs;
        this.e = mActivity;
        this.list = list;
        this.thumbSize = Util.getThumbSize(mActivity, layout);
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        final FileItem item = getItem(position);

        if (convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
            holder.name = (TextView) convertView.findViewById(R.id.title);
            holder.size = (TextView) convertView.findViewById(R.id.detail1);
            holder.owner = (TextView) convertView.findViewById(R.id.detail2);

            if (layout == R.layout.listview_detail) {
                holder.separator = (TextView) convertView.findViewById(R.id.separator);
            }
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (item.shared()) {
            holder.owner.setText(item.getOwner());
        }

        if (layout == R.layout.listview_detail) {
            int separatorVisibility = (position == 0 || !item.getType().equals("folder") && getItem(position - 1).getType().equals("folder")) ? View.VISIBLE : View.GONE;
            String text = (item.getType().equals("folder")) ? "Folders" : "Files";

            holder.separator.setVisibility(separatorVisibility);
            holder.separator.setText(text);
        }

        int checkedVisibility = (list.isItemChecked(position)) ? View.VISIBLE : View.INVISIBLE;
        holder.checked.setVisibility(checkedVisibility);

        if (item.is("image") && item.getThumb() == null && loadthumbs && !itemIsInQueue(item)) {
            if (e.getClass().getSimpleName().equals("RemoteFiles")) {
                // Try to load cached thumb
                Bitmap cachedThumb = (new File(Util.getCacheDir() + item.getID()).exists()) ? Util.getThumb(Util.getCacheDir() + item.getID(), thumbSize) : null;
                if (cachedThumb != null) {
                    item.setThumb(cachedThumb);
                }
                // Load thumb from server
                else {
                    thumbQueue.add(item);
                    if (!thumbLoading) {
                        new LoadThumb().execute();
                    }
                }
            }
            else if (!thumbLoading && e.getClass().getSimpleName().equals("FileSelector")) {
                thumbQueue.add(item);
                new LocalThumb().execute();
            }
        }

        if (item.getIcon() == null) {
            item.setIcon(Util.getIconByName(e, item.getType(), R.drawable.ic_unknown));
        }

        if (layout == R.layout.listview_detail) {
            holder.icon_area.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    list.setItemChecked(position, !list.isItemChecked(position));
                }
            });
        }

        holder.name.setText(item.getFilename());
        holder.size.setText(item.getSize());
        holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.thumb.setImageBitmap(item.getThumb());
        holder.icon.setImageDrawable(item.getIcon());

        return convertView;
    }

    private class ViewHolder {
        ImageView icon;
        ImageView thumb;
        TextView name;
        TextView size;
        TextView owner;
        TextView separator;
        RelativeLayout checked;
        RelativeLayout icon_area;
    }

    public void cancelThumbLoad() {
        thumbQueue.clear();
        thumbLoading = false;
    }

    public void setData(ArrayList<FileItem> arg1) {
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }

    private boolean itemIsInQueue(FileItem item) {
        for (FileItem t : thumbQueue) {
            if (t.getID() == item.getID()) {
                return true;
            }
        }
        return false;
    }

    private class LoadThumb extends AsyncTask<String, Integer, Connection.Response> {
        FileItem item;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            thumbLoading = true;
        }

        @Override
        protected Connection.Response doInBackground(String... info) {
            if (thumbQueue.size() > 0) {
                item = thumbQueue.remove(0);
            }
            else {
                return null;
            }

            File thumb = new File(Util.getCacheDir() + item.getID());

            Connection multipart = new Connection("files", "get");

            multipart.addFormField("target", "[\"" + item.getID() + "\"]");
            multipart.addFormField("width", Integer.toString(thumbSize));
            multipart.addFormField("height", Integer.toString(thumbSize));
            multipart.setDownloadPath(thumb.getParent(), thumb.getName());
            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (res != null) {
                Bitmap bmp = Util.getThumb(Util.getCacheDir() + item.getID(), thumbSize);

                thumbLoading = false;
                if (bmp != null && list != null) {
                    // Update adapter to display thumb
                    item.setThumb(bmp);
                    notifyDataSetChanged();
                }

                if (thumbQueue.size() > 0) {
                    new LoadThumb().execute();
                }
            }
        }
    }

    private class LocalThumb extends AsyncTask<Integer, Bitmap, Bitmap> {
        private FileItem item;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Bitmap doInBackground(Integer... hm) {
            if (thumbQueue.size() > 0) {
                item = thumbQueue.remove(0);
            }
            else {
                return null;
            }

            return Util.getThumb(item.getPath(), thumbSize);
        }
        @Override
        protected void onPostExecute(Bitmap bmp) {
            if (bmp != null) {
                item.setThumb(bmp);
                notifyDataSetChanged();
            }
        }
    }
}