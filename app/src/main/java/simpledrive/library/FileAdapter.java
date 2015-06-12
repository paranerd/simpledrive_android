package simpledrive.library;

import java.util.ArrayList;

import org.simpledrive.R;

import android.app.Activity;
import android.graphics.Typeface;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FileAdapter extends ArrayAdapter<Item> {
	private LayoutInflater layoutInflater;
	static Typeface myTypeface;

	Activity mAtcFragmentActivity;

	public FileAdapter(Activity mActivity, int textViewResourceId) {
		super(mActivity, textViewResourceId);
		mAtcFragmentActivity = mActivity;
		layoutInflater = LayoutInflater.from(mActivity);
		myTypeface = Typeface.createFromAsset(mActivity.getAssets(), "fonts/robotolight.ttf");
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		Item i;
		if(convertView == null) {
			convertView = layoutInflater.inflate(R.layout.list_v, null);
		    if(position % 2 != 0) {
		    	convertView.setBackgroundResource(R.drawable.bkg_light);
		    }
		    else {
		    	convertView.setBackgroundResource(R.drawable.bkg_dark);
		    }
			holder = new ViewHolder();
			holder.name = (TextView) convertView.findViewById(R.id.name);
			holder.size = (TextView) convertView.findViewById(R.id.size);
			holder.thumb = (ImageView) convertView.findViewById(R.id.icon);
			convertView.setTag(holder);
		}
		else {
			holder = (ViewHolder) convertView.getTag();
		    if(position % 2 != 0) {
		    	convertView.setBackgroundResource(R.drawable.bkg_light);
		    }
		    else {
		    	convertView.setBackgroundResource(R.drawable.bkg_dark);
		    }
		}
		
		i = getItem(position);
		
		holder.name.setTypeface(myTypeface);
		
		holder.name.setText(i.getFilename());
		holder.size.setText(i.getData());
		holder.thumb.setImageBitmap(i.getImg());

		return convertView;
	}
	
	static class ViewHolder {
		ImageView thumb;
		TextView name;
		TextView size;
		int position;
	}
	
    public void setData(ArrayList<Item> arg1) {
    	clear();
    	if(arg1 != null) {
    		for (int i=0; i < arg1.size(); i++) {
    			add(arg1.get(i));
    		}
    	}
    }
}
