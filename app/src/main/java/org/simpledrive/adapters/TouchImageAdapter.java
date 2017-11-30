package org.simpledrive.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.simpledrive.R;
import org.simpledrive.activities.ImageViewer;
import org.simpledrive.helper.Downloader;
import org.simpledrive.helper.TouchImageView;
import org.simpledrive.helper.Util;
import org.simpledrive.models.FileItem;

import java.util.ArrayList;

public class TouchImageAdapter extends PagerAdapter {
    private ImageViewer ctx;
    private int displayHeight;
    private int displayWidth;
    private ArrayList<FileItem> images;
    private boolean doLoad = true;

    public TouchImageAdapter(ImageViewer act, ArrayList<FileItem> img) {
        super();
        this.ctx = act;
        this.images = img;

        DisplayMetrics displaymetrics = new DisplayMetrics();
        ctx.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        displayHeight = displaymetrics.heightPixels;
        displayWidth = displaymetrics.widthPixels;
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public View instantiateItem(ViewGroup container, final int position) {
        FileItem item = images.get(position);
        String path = Downloader.isCached(item);
        TouchImageView img = new TouchImageView(container.getContext());

        img.setCustomOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ctx.toggleToolbar();
            }
        });

        if (path != null) {
            if (Util.isGIF(path)) {
                WebView wv = new WebView(container.getContext());

                wv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ctx.toggleToolbar();
                    }
                });

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                int[] dim = Util.scaleImage(ctx, options.outWidth, options.outHeight);
                int img_width = dim[0];
                int img_height = dim[1];

                int topMargin = ((displayHeight - img_height) / 2 > 0) ? (displayHeight - img_height) / 2 : 0;
                int leftMargin = ((displayWidth - img_width) / 2 > 0) ? (displayWidth - img_width) / 2 : 0;

                wv.setBackgroundColor(Color.parseColor("#000000"));
                String url = "file://" + path;

                String data = "<html><head></head><body><img width=" + img_width + " height=" + img_height + " src=\"" + url + "\" /></body></html>";
                wv.loadDataWithBaseURL(null, "<style>body{background-color: black;}img{margin-top: " + topMargin + "px;margin-left: " + leftMargin + "px;display: block;height: " + img_height + "px;width: " + img_width + "px;max-width: 100%;}</style>" + data, "text/html", "UTF-8", null);

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                lp.width = img_width;
                lp.height = img_height;
                wv.setLayoutParams(lp);
                wv.setInitialScale(100);
                container.addView(wv, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                return wv;
            }
            else {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                img.setImageBitmap(bmp);
            }
        }
        else {
            if (item.getThumb() != null) {
                // Set thumbnail as placeholder
                img.setImageBitmap(item.getThumb());
            }
            else {
                // Set placeholder and get image in background
                img.setImageResource(R.drawable.ic_image);
            }
            loadImage(item);
        }

        container.addView(img, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        return img;
    }

    private void loadImage(final FileItem item) {
        Downloader.cache(item, displayWidth, displayHeight, false, new Downloader.TaskListener() {
            @Override
            public void onFinished(boolean success, String path) {
                if (success && doLoad) {
                    // Update adapter to display thumb
                    notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    public void cancelLoad() {
        doLoad = false;
    }
}