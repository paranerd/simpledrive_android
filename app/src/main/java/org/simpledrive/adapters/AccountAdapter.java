package org.simpledrive.adapters;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.models.AccountItem;

import java.util.ArrayList;

public class AccountAdapter extends ArrayAdapter<AccountItem> {
    private LayoutInflater layoutInflater;
    private int layout;
    private AbsListView list;
    private Activity e;

    public AccountAdapter (Activity mActivity, int textViewResourceId, AbsListView list) {
        super(mActivity, textViewResourceId);

        this.layoutInflater = LayoutInflater.from(mActivity);
        this.layout = textViewResourceId;
        this.e = mActivity;
        this.list = list;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        final AccountItem item = getItem(position);

        if(convertView == null) {
            convertView = layoutInflater.inflate(layout, null);

            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.checked = (RelativeLayout) convertView.findViewById(R.id.checked);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.name = (TextView) convertView.findViewById(R.id.detail1);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.icon.setImageBitmap(BitmapFactory.decodeResource(e.getResources(), R.drawable.ic_account));
        assert item != null;
        holder.title.setText(item.getDisplayName());

        if (!item.getName().equals(item.getDisplayName())) {
            holder.name.setText(item.getName());
        }

        if (list.isItemChecked(position)) {
            holder.checked.setVisibility(View.VISIBLE);
        }
        else {
            holder.checked.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    private class ViewHolder {
        ImageView icon;
        TextView title;
        TextView name;
        RelativeLayout checked;
    }

    public void setData(ArrayList<AccountItem> arg1) {
        clear();
        if(arg1 != null) {
            for (int i=0; i < arg1.size(); i++) {
                add(arg1.get(i));
            }
        }
    }
}
