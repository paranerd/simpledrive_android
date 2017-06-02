package org.simpledrive.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.view.PagerAdapter;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.simpledrive.R;
import org.simpledrive.activities.ImageViewer;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.TouchImageView;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.io.File;
import java.util.ArrayList;

public class TouchImageAdapter extends PagerAdapter {
    private ImageViewer e;
    private int displayHeight;
    private int displayWidth;
    private ArrayList<FileItem> images;
    private boolean doLoad = true;

    public TouchImageAdapter(ImageViewer act, ArrayList<FileItem> img) {
        super();
        this.e = act;
        this.images = img;

        DisplayMetrics displaymetrics = new DisplayMetrics();
        e.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        displayHeight = displaymetrics.heightPixels;
        displayWidth = displaymetrics.widthPixels;
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public View instantiateItem(ViewGroup container, final int position) {
        String path = Util.getCacheDir() + images.get(position).getID();
        TouchImageView img = new TouchImageView(container.getContext());
        WebView wv = null;

        img.setCustomOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                e.toggleToolbar();
            }
        });

        if (new File(path).exists()) {
            if (Util.isGIF(path)) {
                wv = new WebView(container.getContext());

                wv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        e.toggleToolbar();
                    }
                });

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                int[] dim = Util.scaleImage(e, options.outWidth, options.outHeight);
                int img_height = dim[1];
                int img_width = dim[0];

                int topMargin = ((displayHeight - img_height) / 2 > 0) ? (displayHeight - img_height) / 2 : 0;
                int leftMargin = ((displayWidth - img_width) / 2 > 0) ? (displayWidth - img_width) / 2 : 0;

                wv.setBackgroundColor(Color.parseColor("#000000"));
                String url = "file://" + path;

                String data = "<html><head></head><body><img width=" + img_width + " height=" + img_height + " src=\"" + url + "\" /></body></html>";
                wv.loadDataWithBaseURL(null, "<style>body{background-color: black;}img{margin-top: " + topMargin + "px;margin-left: " + leftMargin + "px;display: block;height: " + img_height + "px;width: " + img_width + "px;max-width: 100%;}</style>" + data, "text/html", "UTF-8", null);

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                lp.height = img_height;
                lp.width = img_width;
                wv.setLayoutParams(lp);
                wv.setInitialScale(100);
            }
            else {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                img.setImageBitmap(bmp);
            }
        }
        else {
            // Set placeholder and get image in background
            img.setImageResource(R.drawable.ic_image);
            new LoadImage(img, images.get(position), path);
        }

        if (wv != null) {
            container.addView(wv, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            return wv;
        }

        container.addView(img, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        return img;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    public void cancelThumbLoad() {
        doLoad = false;
    }

    private class LoadImage extends AsyncTask<String, Integer, Connection.Response> {
        TouchImageView img;
        FileItem file;
        String imgPath;

        public LoadImage(final TouchImageView img, FileItem file, String imgPath) {
            this.img = img;
            this.file = file;
            this.imgPath= imgPath;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Connection.Response doInBackground(String... info) {
            if (!doLoad) {
                return null;
            }

            File thumb = new File(this.imgPath);

            Connection multipart = new Connection("files", "get");
            multipart.addFormField("target", "[\"" + file.getID() + "\"]");
            multipart.addFormField("width", displayWidth + "");
            multipart.addFormField("height", displayHeight + "");
            multipart.setDownloadPath(thumb.getParent(), thumb.getName());
            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if(!doLoad) {
                return;
            }

            Bitmap bmp = BitmapFactory.decodeFile(this.imgPath);

            if (bmp != null) {
                // Update adapter to display thumb
                this.img.setImageBitmap(bmp);
                notifyDataSetChanged();
            }
        }
    }
}