package org.simpledrive.adapters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
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
import org.simpledrive.helper.FileItem;
import org.simpledrive.helper.Util;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

public class FileAdapter extends ArrayAdapter<FileItem> implements Serializable {
    private LayoutInflater layoutInflater;
    private int layout;
    private int gridSize;
    private boolean loadthumbs = false;
    private ArrayList<FileItem> thumbQueue = new ArrayList<>();
    private boolean thumbLoading = false;
    private Integer firstFilePos;
    private Activity e;
    private AbsListView list;

    public FileAdapter(Activity mActivity, int textViewResourceId, AbsListView list, int gridSize, boolean loadthumbs, int firstFilePos) {
        super(mActivity, textViewResourceId);
        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = textViewResourceId;
        this.gridSize = gridSize;
        this.loadthumbs = loadthumbs;
        this.firstFilePos = firstFilePos;
        this.e = mActivity;
        this.list = list;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final FileItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.size = (TextView) convertView.findViewById(R.id.size);
            holder.owner = (TextView) convertView.findViewById(R.id.owner);

            if (layout == R.layout.filegrid) {
                holder.wrapper = (RelativeLayout) convertView.findViewById(R.id.wrapper);
                holder.wrapper.setLayoutParams(new RelativeLayout.LayoutParams(gridSize, gridSize));
            }
            else {
                holder.separator = (TextView) convertView.findViewById(R.id.separator);
            }
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.owner.setText(item.getOwner());
        holder.name.setText(item.getFilename());
        holder.size.setText(item.getSize());
        holder.icon.setImageBitmap(item.getIcon());

        if (layout == R.layout.filelist) {
            int visibility = (position == 0 || (firstFilePos != null && position == firstFilePos)) ? View.VISIBLE : View.GONE;
            holder.separator.setVisibility(visibility);

            String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
            holder.separator.setText(text);
        }

        if (list.isItemChecked(position)) {
            holder.checked.setVisibility(View.VISIBLE);
            if(layout == R.layout.filegrid && item.is("image")) {
                holder.checked.setBackgroundColor(ContextCompat.getColor(e, R.color.transparentgreen));
            }
        }
        else {
            holder.checked.setVisibility(View.INVISIBLE);
            holder.checked.setBackgroundColor(ContextCompat.getColor(e, R.color.transparent));
        }

        if (item.is("image")) {
            if (item.getThumb() == null && loadthumbs) {
                thumbQueue.add(item);

                if (!thumbLoading && e.getClass().getSimpleName().equals("RemoteFiles")) {
                    new LoadThumb().execute();
                }
                else if (!thumbLoading && e.getClass().getSimpleName().equals("FileSelector")) {
                    new LocalThumb().execute();
                }
            }
            else {
                holder.thumb.setImageBitmap(item.getThumb());
            }
        }
        else {
            holder.thumb.setImageBitmap(null);
        }

        if (layout == R.layout.filelist) {
            holder.icon_area.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //lastSelected = position;
                    list.setItemChecked(position, !list.isItemChecked(position));
                }
            });
        }

        return convertView;
    }

    private class ViewHolder {
        RelativeLayout wrapper;
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

    private class LoadThumb extends AsyncTask<String, Integer, Connection.Response> {
        FileItem item;
        String size;
        String filepath;

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

            DisplayMetrics displaymetrics = new DisplayMetrics();
            e.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

            size = (layout == R.layout.filelist) ? Util.dpToPx(100) + "" : Integer.toString(displaymetrics.widthPixels / 2);
            String file = item.getID();
            filepath = item.getThumbPath();
            Log.i("debug", "file: " + file);
            Log.i("debug", "filepath: " + filepath);

            File thumb = new File(filepath);

            Connection multipart = new Connection("files", "read");

            multipart.addFormField("target", "[\"" + file + "\"]");
            multipart.addFormField("width", size);
            multipart.addFormField("height", size);
            multipart.setDownloadPath(thumb.getParent(), thumb.getName());
            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (res != null) {
                Bitmap bmp = Util.getThumb(filepath, Integer.valueOf(size));

                thumbLoading = false;
                if (bmp != null && list != null) {
                    // Update adapter to display thumb
                    item.setThumb(bmp);
                    notifyDataSetChanged();
                }

                if (thumbQueue.size() > 0) {
                    new LoadThumb().execute();
                }

                /*if(filename.substring(filename.length() - 3).equals("png")) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos);
                } else {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                }*/
            }
        }
    }

    public class LocalThumb extends AsyncTask<Integer, Bitmap, Bitmap> {
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

            String path = item.getPath();

            int thumbSize = Util.dpToPx(40);
            return Util.getThumb(path, thumbSize);
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