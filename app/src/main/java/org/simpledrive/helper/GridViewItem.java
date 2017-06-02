package org.simpledrive.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class GridViewItem extends RelativeLayout {
    public GridViewItem(Context ctx) {
        super(ctx);
    }

    public GridViewItem(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    public GridViewItem(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}