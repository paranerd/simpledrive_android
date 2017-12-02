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
import org.simpledrive.models.FileItem;

import java.util.ArrayList;

public class ImageViewer extends AppCompatActivity {
    private TouchImageAdapter adapter;
    private boolean titleVisible = true;
    private Toolbar toolbar;
    private ArrayList<FileItem> images;

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (adapter != null) {
            adapter.cancelLoad();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        images = RemoteFiles.getAllImages();

        initInterface();
        initToolbar();
    }

    private void initInterface() {
        setContentView(R.layout.activity_imageviewer);

        ExtendedViewPager mViewPager = (ExtendedViewPager) findViewById(R.id.view_pager);
        adapter = new TouchImageAdapter(this, images);
        mViewPager.setAdapter(adapter);
        mViewPager.setCurrentItem(getIntent().getExtras().getInt("position"));

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (toolbar != null) {
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

    private void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.halfblack));
            toolbar.setTitleTextColor(Color.parseColor("#eeeeee"));
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    public void toggleToolbar() {
        if (titleVisible && toolbar != null) {
            toolbar.animate().translationY(-toolbar.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
            titleVisible = false;
        }
        else if (toolbar != null) {
            toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
            titleVisible = true;
        }
    }
}