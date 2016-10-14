package org.simpledrive.adapters;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.helper.LogItem;

import java.util.ArrayList;

public class LogAdapter extends ArrayAdapter<LogItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private Activity e;

    public LogAdapter(Activity mActivity, int textViewResourceId) {
        super(mActivity, textViewResourceId);
        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = textViewResourceId;
        this.e = mActivity;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final LogItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.icon_circle = (FrameLayout) convertView.findViewById(R.id.icon_circle);
            holder.message = (TextView) convertView.findViewById(R.id.message);
            holder.user = (TextView) convertView.findViewById(R.id.user);
            holder.date = (TextView) convertView.findViewById(R.id.date);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.message.setText(item.getMessage());
        holder.user.setText(item.getUser());
        holder.date.setText(item.getDate());
        holder.icon.setImageBitmap(item.getIcon());

        int color;

        switch (item.getType()) {
            case "0":
                color = R.color.darkgreen;
                break;
            case "1":
                color = R.color.orange;
                break;
            default:
                color = R.color.red;
                break;
        }

        Drawable drawable = ContextCompat.getDrawable(e, R.drawable.circle_drawable);
        drawable.setColorFilter(ContextCompat.getColor(e, color), PorterDuff.Mode.SRC_ATOP);

        holder.icon_circle.setBackground(drawable);

        return convertView;
    }

    class ViewHolder {
        ImageView icon;
        TextView message;
        TextView user;
        TextView date;
        FrameLayout icon_circle;
    }

    public void setData(ArrayList<LogItem> arg1) {
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }
}
