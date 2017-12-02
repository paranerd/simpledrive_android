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
import org.simpledrive.helper.Downloader;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class FileAdapter extends ArrayAdapter<FileItem> implements Serializable {
    private LayoutInflater layoutInflater;
    private int layout;
    private boolean loadthumbs = false;
    private static boolean thumbLoading = false;
    private boolean blockLoading = false;
    private AppCompatActivity ctx;
    private AbsListView list;
    private Integer thumbSize;

    public FileAdapter(AppCompatActivity ctx, int layoutResourceId, AbsListView list) {
        this(ctx, layoutResourceId, list, Preferences.getInstance(ctx).read(Preferences.TAG_LOAD_THUMB, false));
    }

    public FileAdapter(AppCompatActivity ctx, int layoutResourceId, AbsListView list, boolean loadthumbs) {
        super(ctx, layoutResourceId);
        this.layoutInflater = LayoutInflater.from(ctx);
        this.layout = layoutResourceId;
        this.loadthumbs = loadthumbs;
        this.ctx = ctx;
        this.list = list;
        this.thumbSize = Util.getThumbSize(ctx, layout);
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

        if (item.is("image") && item.getThumb() == null && !blockLoading) {
            if (ctx.getClass().getSimpleName().equals("RemoteFiles")) {
                // Try to load cached thumb
                String cachedThumb = Downloader.isThumbnailCached(item, thumbSize);
                if (cachedThumb != null) {
                    Bitmap thumb = Util.resizeImage(cachedThumb, thumbSize);
                    item.setThumb(thumb);
                }
                // Load thumb from server
                else if (loadthumbs  && !thumbLoading) {
                    thumbLoading = true;
                    loadRemoteThumb(item, thumbSize);
                }
            }
            else if (!thumbLoading && ctx.getClass().getSimpleName().equals("FileSelector")) {
                thumbLoading = true;
                new LoadLocalThumb(this, item, thumbSize).execute();
            }
        }

        if (item.getIcon() == null) {
            item.setIcon(Util.getIconByName(ctx, item.getType(), R.drawable.ic_unknown));
        }

        if (layout == R.layout.listview_detail) {
            holder.icon_area.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    list.setItemChecked(position, !list.isItemChecked(position));
                }
            });
        }

        int checkedVisibility = (list.isItemChecked(position)) ? View.VISIBLE : View.INVISIBLE;
        holder.checked.setVisibility(checkedVisibility);

        int iconVisibility = (item.getThumb() == null) ? View.VISIBLE : View.INVISIBLE;
        holder.icon.setVisibility(iconVisibility);

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
        blockLoading = true;
        thumbLoading = false;
    }

    public void setData(ArrayList<FileItem> arg1) {
        blockLoading = false;
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }

    private void loadRemoteThumb(final FileItem item, final int size) {
        Downloader.cache(item, size, size, true, new Downloader.TaskListener() {
            @Override
            public void onFinished(boolean success, String path) {
                thumbLoading = false;
                if (success && list != null && !blockLoading) {
                    // Update adapter to display thumb
                    notifyDataSetChanged();
                }
            }
        });
    }

    private static class LoadLocalThumb extends AsyncTask<Integer, Void, Bitmap> {
        private WeakReference<FileAdapter> ref;
        private FileItem item;
        private int size;

        LoadLocalThumb(FileAdapter ctx, FileItem item, int size) {
            this.ref = new WeakReference<>(ctx);
            this.item = item;
            this.size = size;
        }

        @Override
        protected Bitmap doInBackground(Integer... hm) {
            return Util.resizeImage(this.item.getPath(), size);
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            thumbLoading = false;
            if (ref.get() == null) {
                return;
            }

            final FileAdapter act = ref.get();
            if (bmp != null) {
                act.notifyDataSetChanged();
            }
        }
    }
}