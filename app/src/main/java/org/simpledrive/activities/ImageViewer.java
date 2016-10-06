package org.simpledrive.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import org.simpledrive.R;
import org.simpledrive.adapters.TouchImageAdapter;
import org.simpledrive.helper.ExtendedViewPager;
import org.simpledrive.helper.FileItem;

import java.util.ArrayList;

public class ImageViewer extends AppCompatActivity {
    public static ExtendedViewPager mViewPager;
    public static TouchImageAdapter mAdapter;
    private static boolean titleVisible = true;
    private static Toolbar toolbar;
    private ImageViewer e;
    public static ArrayList<FileItem> images;

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAdapter != null) {
            mAdapter.cancel();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imageviewer);

        images = RemoteFiles.getAllImages();
        e = this;

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setBackgroundColor(ContextCompat.getColor(e, R.color.halfblack));
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
        mAdapter = new TouchImageAdapter(e, images);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(pos);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if(toolbar != null) {
                    toolbar.setTitle(images.get(position).getFilename());
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
}