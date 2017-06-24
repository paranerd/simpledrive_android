package org.simpledrive.adapters;

import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.models.VaultItem;

import java.util.ArrayList;

public class VaultAdapter extends ArrayAdapter<VaultItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private AbsListView list;
    private AppCompatActivity e;

    public VaultAdapter (AppCompatActivity mActivity, int textViewResourceId, AbsListView list) {
        super(mActivity, textViewResourceId);

        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = textViewResourceId;
        this.e = mActivity;
        this.list = list;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final VaultItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon= (ImageView) convertView.findViewById(R.id.thumb);
            holder.icon_area = (RelativeLayout) convertView.findViewById(R.id.icon_area);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.detail1 = (TextView) convertView.findViewById(R.id.detail1);
            holder.detail2 = (TextView) convertView.findViewById(R.id.detail2);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.title.setText(item.getTitle());
        holder.detail1.setText(item.getType());
        holder.detail2.setText("");
        holder.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        holder.icon.setImageBitmap(item.getIconBmp());

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
        TextView title;
        TextView detail1;
        TextView detail2;
        RelativeLayout icon_area;
        RelativeLayout checked;
    }

    public void setData(ArrayList<VaultItem> arg1) {
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }
}
