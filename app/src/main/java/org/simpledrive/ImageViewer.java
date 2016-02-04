package org.simpledrive;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import simpledrive.lib.Connection;

public class ImageViewer extends ActionBarActivity {
    public static ExtendedViewPager mViewPager;
    private static boolean titleVisible = true;
    private static Toolbar toolbar;
    private ImageViewer e;
    private int width;
    private int height;
    private AsyncTask loader;

    public static ArrayList<HashMap<String, String>> images;

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(loader != null) {
            loader.cancel(true);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imageviewer);

        images = RemoteFiles.getAllImages();
        e = this;

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        height = displaymetrics.heightPixels;
        width = displaymetrics.widthPixels;

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setBackgroundColor(getResources().getColor(R.color.halfblack));
            toolbar.setTitleTextColor(Color.parseColor("#eeeeee"));
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    e.finish();
                }
            });
        }

        Bundle extras = getIntent().getExtras();
        final int pos = extras.getInt("position");

        mViewPager = (ExtendedViewPager) findViewById(R.id.view_pager);
        TouchImageAdapter mAdapter = new TouchImageAdapter();
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(pos);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if(toolbar != null) {
                    SpannableString s = new SpannableString(images.get(position).get("filename"));
                    s.setSpan(new TypefaceSpan("fonts/robotolight.ttf"), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    toolbar.setTitle(s);
                }
            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    public static void toggleToolbar() {
        if(titleVisible && toolbar != null) {
            toolbar.animate().translationY(-toolbar.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
            titleVisible = false;
        }
        else if(toolbar != null) {
            toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
            titleVisible = true;
        }
    }

    class TouchImageAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public View instantiateItem(ViewGroup container, final int position) {
            String imgPath = images.get(position).get("path");
            String thumbPath = images.get(position).get("thumbPath");
            TouchImageView img = new TouchImageView(container.getContext());

            img.setCustomOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleToolbar();
                }
            });

            if(new File(imgPath).exists()) {
                // Load image
                Bitmap bmp = BitmapFactory.decodeFile(imgPath);
                img.setImageBitmap(bmp);
            } else if(new File(thumbPath).exists()) {
                // Load thumbnail and get image in background
                Bitmap bmp = BitmapFactory.decodeFile(thumbPath);
                img.setImageBitmap(bmp);
                //loadImage(thumbPath, img, images.get(position).get("file"), images.get(position).get("filename"), imgPath);
                loader = new LoadImage(thumbPath, img, images.get(position).get("file"), images.get(position).get("filename"), imgPath).execute();
            } else {
                // Set placeholder and get image in background
                img.setImageResource(R.drawable.ic_image);
                //loadImage(null, img, images.get(position).get("file"), images.get(position).get("filename"), imgPath);
                new LoadImage(thumbPath, img, images.get(position).get("file"), images.get(position).get("filename"), imgPath).execute();
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
    }

    private class LoadImage extends AsyncTask<String, Integer, HashMap<String, String>> {
        String thumbPath;
        TouchImageView img;
        String file;
        String filename;
        String path;

        public LoadImage(final String thumbPath, final TouchImageView img, String file, String filename, String path) {
            this.thumbPath = thumbPath;
            this.img = img;
            this.file = file;
            this.filename = filename;
            this.path = path;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected HashMap<String, String> doInBackground(String... info) {
            File thumb = new File(path);

            Connection multipart = new Connection("files", "img", null);
            multipart.addFormField("target", file);
            multipart.addFormField("width", width + "");
            multipart.addFormField("height", height + "");
            multipart.addFormField("type", "img");
            multipart.setDownloadPath(thumb.getParent(), thumb.getName());
            return multipart.finish();
        }

        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            if(this.isCancelled()) {
                return;
            }

            Bitmap bmp = BitmapFactory.decodeFile(path);

            if (bmp != null && mViewPager != null) {
                // Update adapter to display thumb
                img.setImageBitmap(bmp);
                mViewPager.getAdapter().notifyDataSetChanged();
            }

            if(thumbPath != null) {
                // Overrides the thumbnail with the image (may consume too much memory with many - and then bigger - thumbnails to display)
                //new File(thumbPath).delete();
            }
        }
    }
}