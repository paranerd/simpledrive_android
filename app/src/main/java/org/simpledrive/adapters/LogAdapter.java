package org.simpledrive.adapters;

import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.helper.Util;
import org.simpledrive.models.LogItem;

import java.util.ArrayList;

public class LogAdapter extends ArrayAdapter<LogItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private AppCompatActivity ctx;

    public LogAdapter(AppCompatActivity ctx, int textViewResourceId) {
        super(ctx, textViewResourceId);
        this.layoutInflater = LayoutInflater.from(ctx);
        this.layout = textViewResourceId;
        this.ctx = ctx;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final LogItem item = getItem(position);

        if (convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.message = (TextView) convertView.findViewById(R.id.title);
            holder.user = (TextView) convertView.findViewById(R.id.detail1);
            holder.date = (TextView) convertView.findViewById(R.id.detail2);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (item.getIcon() == null) {
            item.setIcon(Util.getIconByName(ctx, item.getType(), R.drawable.ic_error));
        }

        holder.message.setText(item.getMessage());
        holder.user.setText(item.getUser());
        holder.date.setText(item.getDate());
        holder.icon.setImageDrawable(item.getIcon());

        int color;

        switch (item.getType()) {
            case "info":
                color = R.color.darkgreen;
                break;
            case "warning":
                color = R.color.orange;
                break;
            default:
                color = R.color.red;
                break;
        }

        holder.icon.setColorFilter(ContextCompat.getColor(ctx, color));

        return convertView;
    }

    private class ViewHolder {
        ImageView icon;
        TextView message;
        TextView user;
        TextView date;
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
