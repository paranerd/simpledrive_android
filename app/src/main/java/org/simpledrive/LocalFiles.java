package org.simpledrive;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import simpledrive.library.FileAdapter;
import simpledrive.library.FileLoader;
import simpledrive.library.Item;

public class LocalFiles extends FragmentActivity implements LoaderManager.LoaderCallbacks<ArrayList<Item>> {
	static FileAdapter mAdapter;
	
	static ListView list;
	static String server = "http";
	public static final String PREFS_NAME = "org.simpledrive.shared_pref";
	SharedPreferences settings;
	
	static Typeface myTypeface;
	
	static LocalFiles act;
	
	static ArrayList<Integer> scrollPos = new ArrayList<Integer>();
	static Integer scrollArrayPos = 0;
	
	private static File currentDir;
	public static String currentPath;
	
	public void refresh() {
		getSupportLoaderManager().restartLoader(0, null, this).forceLoad();
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        act = this;
        settings = getSharedPreferences(PREFS_NAME, 0);
        server  = settings.getString("server", "");
        
        setContentView(R.layout.local_files);
        list = (ListView)findViewById(R.id.locallist);
        list.setEmptyView(findViewById(R.id.local_empty_list_item));
		
		registerForContextMenu(list);
		
		myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
		TextView empty = (TextView) findViewById(R.id.local_empty_list_item);
		empty.setTypeface(myTypeface);
		
        currentDir = new File(Environment.getExternalStorageDirectory() + "/");
        currentPath = Environment.getExternalStorageDirectory() + "/";
        FileLoader.currentDir = currentDir;

		getSupportLoaderManager().initLoader(0, null, this);
		mAdapter = new FileAdapter(act, R.layout.list_v);
		list.setAdapter(mAdapter);
		
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
					Item item = FileLoader.directories.get(position);
					if(item.getType().equals("folder")) {
						// Remove everything in scroll array below current folder-level
						for(int i = scrollPos.size() - 1; i >= scrollArrayPos; i--) {
							scrollPos.remove(i);
						}
						scrollPos.add(scrollArrayPos, position);
						scrollArrayPos++;
							currentDir = new File(item.getPath());
							currentPath = item.getPath();
							FileLoader.currentDir = currentDir;
							refresh();
					}
					else
					{
						Intent returnIntent = new Intent();
						returnIntent.putExtra("path", FileLoader.directories.get(position).getPath());
						setResult(RESULT_OK, returnIntent);
						finish();
					}
			}
		});
    }
    
	
	@Override
	public Loader<ArrayList<Item>> onCreateLoader(int arg0, Bundle arg1) {
		return new FileLoader(act, mAdapter);
	}

	@Override
	public void onLoaderReset(Loader<ArrayList<Item>> arg0) {
		mAdapter.setData(null);
	}
	
	@Override
	public void onLoadFinished(Loader<ArrayList<Item>> arg0, ArrayList<Item> arg1) {
		mAdapter.setData(arg1);
		if(scrollPos.size() > 0 && scrollArrayPos < scrollPos.size()) {
			list.setSelection(scrollPos.get(scrollArrayPos));
		}
	}
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);

    	//int OPEN_ID = 4;
    	int UPLOAD_ID = 5;
    	
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

    	//String type = FileLoader.directories.get(info.position).getType();

		menu.add(Menu.NONE, UPLOAD_ID, 0, "Upload");
    	/*if(type.equals("folder")) {
        	menu.add(Menu.NONE, OPEN_ID, 0, "Open");
    	}
    	else {
    		menu.add(Menu.NONE, UPLOAD_ID, 0, "Upload");
    	}*/
    }

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

		switch(item.getItemId()) {
			case 4:
				Toast.makeText(act, "Open", Toast.LENGTH_SHORT).show();
				return true;
			case 5:
				Toast.makeText(act, "Upload", Toast.LENGTH_SHORT).show();
				Intent returnIntent = new Intent();
				returnIntent.putExtra("path", FileLoader.directories.get(info.position).getPath());
				setResult(RESULT_OK, returnIntent);
				finish();
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}
    
    public void onBackPressed() {
		if(!FileLoader.prevDir.equals("")) {
			if(scrollArrayPos == 0) {
				scrollPos.add(0, 0);
			}
			else {
				scrollArrayPos--;
			}
			currentDir = new File(FileLoader.prevDir);
			FileLoader.currentDir = currentDir;
			refresh();
		}
		else {
			Intent returnIntent = new Intent();
			setResult(RESULT_CANCELED, returnIntent);
			finish();
		}
    }
}