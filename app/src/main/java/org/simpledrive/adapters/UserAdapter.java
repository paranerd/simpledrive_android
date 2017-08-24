package org.simpledrive.adapters;

import android.support.v7.app.AppCompatActivity;
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
import org.simpledrive.helper.Util;
import org.simpledrive.models.UserItem;

import java.util.ArrayList;

public class UserAdapter extends ArrayAdapter<UserItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private AbsListView list;
    private AppCompatActivity ctx;

    public UserAdapter (AppCompatActivity ctx, int textViewResourceId, AbsListView list) {
        super(ctx, textViewResourceId);

        this.layoutInflater = LayoutInflater.from(ctx);
        this.layout = textViewResourceId;
        this.list = list;
        this.ctx = ctx;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final UserItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.name = (TextView) convertView.findViewById(R.id.title);
            holder.mode = (TextView) convertView.findViewById(R.id.detail1);
            holder.date = (TextView) convertView.findViewById(R.id.detail2);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (item.getIcon() == null) {
            Log.i("debug", "set icon: " + item.getMode());
            item.setIcon(Util.getIconByName(ctx, item.getMode(), R.drawable.ic_user));
        }

        holder.name.setText(item.getUsername());
        holder.mode.setText(item.getMode());
        holder.date.setText("");
        holder.icon.setImageDrawable(item.getIcon());

        if (list.isItemChecked(position)) {
            holder.checked.setVisibility(View.VISIBLE);
        }
        else {
            holder.checked.setVisibility(View.INVISIBLE);
        }

        holder.icon_area.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                list.setItemChecked(position, list.isItemChecked(position));
                notifyDataSetChanged();
            }
        });

        return convertView;
    }

    private class ViewHolder {
        ImageView icon;
        TextView name;
        TextView mode;
        TextView date;
        RelativeLayout icon_area;
        RelativeLayout checked;
    }

    public void setData(ArrayList<UserItem> arg1) {
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }
}
