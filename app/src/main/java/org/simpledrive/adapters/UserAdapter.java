package org.simpledrive.adapters;

import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.helper.UserItem;

import java.util.ArrayList;

public class UserAdapter extends ArrayAdapter<UserItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private AbsListView list;
    private Activity e;

    public UserAdapter (Activity mActivity, int textViewResourceId, AbsListView list) {
        super(mActivity, textViewResourceId);

        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = textViewResourceId;
        this.e = mActivity;
        this.list = list;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final UserItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.icon_circle = (FrameLayout) convertView.findViewById(R.id.icon_circle);
            holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.name = (TextView) convertView.findViewById(R.id.username);
            holder.mode = (TextView) convertView.findViewById(R.id.mode);
            holder.date = (TextView) convertView.findViewById(R.id.date);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.name.setText(item.getUsername());
        holder.mode.setText(item.getMode());
        holder.date.setText("");
        holder.icon.setImageBitmap(item.getIcon());

        if (list.isItemChecked(position)) {
            holder.checked.setVisibility(View.VISIBLE);
        }
        else {
            holder.checked.setVisibility(View.INVISIBLE);
            holder.checked.setBackgroundColor(ContextCompat.getColor(e, R.color.transparent));
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

    class ViewHolder {
        ImageView icon;
        TextView name;
        TextView mode;
        TextView date;
        FrameLayout icon_circle;
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
