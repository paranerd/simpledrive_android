package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.lib.AudioService;
import simpledrive.lib.AudioService.LocalBinder;
import simpledrive.lib.Connection;
import simpledrive.lib.Download.DownloadListener;
import simpledrive.lib.Helper;
import simpledrive.lib.ImageLoader;
import simpledrive.lib.Upload.ProgressListener;

public class RemoteFiles extends ActionBarActivity
{
    public static RemoteFiles e;
    public static String audioFilename;
    private static String server;
    private static Typeface myTypeface;
    private static AbsListView list;
    private static ArrayList<HashMap<String, String>> oslist;
    private static HashMap<Integer, JSONObject> allElements = new HashMap<>();
    private static HashMap<Integer, JSONObject> selectedElem_map= new HashMap<>();
    private static ArrayList<JSONObject> hierarchy = new ArrayList<>();
    private static String username = "";
    private static AudioService mPlayerService;
    private boolean mBound = false;
    private static int displayMode = 0;
    private final String tmp_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private TextView empty;
    private static String glob_layout;
    private SharedPreferences settings;
    private boolean longClicked = false;
    private Integer firstFilePos;
    private ArrayList<HashMap<String, String>> upload_queue = new ArrayList<>();
    private int upload_current = 0;
    private int upload_total = 0;
    private int upload_successful = 0;
    private static boolean uploading = false;
    private static ArrayList<String> uploadsPending;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Toolbar toolbar;
    private ImageButton bUpload;
    private ImageButton bCreate;
    private ArrayList<HashMap<String, String>> thumbQueue = new ArrayList<>();
    private ImageLoader imgLoader;

    ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			mPlayerService = null;
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			LocalBinder binder = (LocalBinder) service;
			mPlayerService = binder.getService();
			mBound = true;
		}  
  };

  private class ListContent extends AsyncTask<String, String, JSONArray> {
      ProgressDialog pDialog;

    	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            empty.setText("Loading files...");
            pDialog = new ProgressDialog(RemoteFiles.this);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
    	}
    	
    	@Override
        protected JSONArray doInBackground(String... args) {
    		String url = server + "php/files_api.php";
    		HashMap<String, String> data = new HashMap<>();

            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
    		data.put("mode", Integer.toString(displayMode));
            data.put("action", "list");

            return Connection.forJSON(url, data);
    	}
    	 @Override
         protected void onPostExecute(JSONArray value) {
             pDialog.dismiss();
             mSwipeRefreshLayout.setRefreshing(false);
    		 listContent(value);
    	 }
    }

    private void unselectAll() {
        selectedElem_map = new HashMap<>();
        invalidateOptionsMenu();
        ((SimpleAdapter) list.getAdapter()).notifyDataSetChanged();
    }
  
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void listContent(final JSONArray json) {
        try {
            if(json == null || (json.length() != 0 && json.getJSONObject(0).has("error"))) {
                new Connect().execute();
                return;
            }

            if(imgLoader != null) {
                imgLoader.cancel(true);
                imgLoader = null;
            }

            oslist = new ArrayList<>();
            thumbQueue = new ArrayList<>();
            firstFilePos = null;

            for(int i = 0; i < json.length(); i++){
                JSONObject c = json.getJSONObject(i);
                HashMap<String, String> map = new HashMap<>();

                Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String n = it.next();
                    String size = (n.equals("size")) ? Helper.convertSize(c.getString(n)) : c.getString(n);
                    map.put(n, size);
                }
                allElements.put(i, c);
                oslist.add(map);
            }

            String emptyText = (oslist.size() == 0) ? "Nothing to see here." : "";
            empty.setText(emptyText);
			 
            final int[] image = new int[] { R.drawable.folder_thumb, R.drawable.unknown_thumb, R.drawable.audio_thumb, R.drawable.pdf_thumb, R.drawable.image_thumb};

            List<HashMap<String, String>> listinfo = new ArrayList<>();
            listinfo.clear();

            for(int i = 0; i < oslist.size(); i++)
            {
                HashMap<String, String> elem = oslist.get(i);
                if(uploadsPending != null && !elem.get("type").equals("folder")) {
                    continue;
                }

                HashMap<String, String> hm = new HashMap<>();
                if(displayMode == 3) {
                    int pos = elem.get("filename").lastIndexOf("_trash");
                    hm.put("filename", elem.get("filename").substring(0, pos));
                }
                else {
                    hm.put("filename", elem.get("filename"));
                }

                if(elem.get("type").equals("folder")) {
                    hm.put("image", Integer.toString(image[0]));
                }
                else if(elem.get("type").equals("audio")) {
                    hm.put("image", Integer.toString(image[2]));
                }
                else if(elem.get("type").equals("pdf")) {
                    hm.put("image", Integer.toString(image[3]));
                }
                else if(elem.get("type").equals("image")) {
                    hm.put("image", Integer.toString(image[4]));
                }
                else {
                    hm.put("image", Integer.toString(image[1]));
                }

                if(elem.get("type").equals("folder")) {
                    hm.put("size", "");
                }
                else {
                    hm.put("size", elem.get("size"));
                    if(firstFilePos == null) {
                        firstFilePos = i;
                    }
                }

                if(!elem.get("owner").equals(username)) {
                    hm.put("owner", elem.get("owner"));
                }
                else if(!elem.get("closehash").equals("null")) {
                    hm.put("owner", "shared");
                }
                listinfo.add(hm);
            }

            int layout = (glob_layout.equals("list")) ? R.layout.list_v : R.layout.gridview;
            SimpleAdapter adapter = new SimpleAdapter(e, listinfo, layout, new String[]{"image", "filename", "size", "owner"}, new int[]{R.id.icon, R.id.name, R.id.size, R.id.owner})
            {
                @Override
                public View getView(final int position, final View convertView, ViewGroup parent)
                {
                    final View view = super.getView(position, convertView, parent);
                    final TextView tvName = (TextView) view.findViewById(R.id.name);
                    final TextView tvType = (TextView) view.findViewById(R.id.size);
                    final TextView separator = (TextView) view.findViewById(R.id.separator);
                    final ImageView imgview = (ImageView) view.findViewById(R.id.icon);
                    final RelativeLayout wrapper = (RelativeLayout) view.findViewById(R.id.wrapper);
                    final RelativeLayout inner = (RelativeLayout) view.findViewById(R.id.inner);

                    tvName.setTypeface(myTypeface);
                    tvType.setTypeface(myTypeface);

                    if(glob_layout.equals("list"))
                    {
                        AbsListView.LayoutParams lp = (AbsListView.LayoutParams) wrapper.getLayoutParams();
                        RelativeLayout.LayoutParams lp2 = (RelativeLayout.LayoutParams) inner.getLayoutParams();

                        if(position == 0 || (firstFilePos != null && position == firstFilePos))
                        {
                            separator.setVisibility(View.VISIBLE);
                            lp.height = Helper.dpToPx(120);
                            lp2.setMargins(0, Helper.dpToPx(48), 0, 0);
                        } else {
                            separator.setVisibility(View.GONE);
                            lp.height = Helper.dpToPx(72);
                            lp2.setMargins(0, 0, 0, 0);
                        }
                        String text = (firstFilePos != null && position == firstFilePos) ? "Files" : "Folders";
                        separator.setText(text);
                        wrapper.setLayoutParams(lp);
                        inner.setLayoutParams(lp2);
                    }

                    if (!oslist.get(position).get("type").equals("image") && glob_layout.equals("grid"))
                    {
                        DisplayMetrics displaymetrics = new DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                        imgview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) imgview.getLayoutParams();
                        lp.height = displaymetrics.widthPixels / 8;
                        lp.width = displaymetrics.widthPixels / 8;
                        imgview.setLayoutParams(lp);
                    } else if (glob_layout.equals("grid")) {
                        imgview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) imgview.getLayoutParams();
                        lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                        lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                        imgview.setLayoutParams(lp);
                    }

                    int gravity = (oslist.get(position).get("type").equals("folder") || glob_layout.equals("grid")) ? Gravity.CENTER_VERTICAL : Gravity.TOP;
                    tvName.setGravity(gravity);

                    if (selectedElem_map.get(position) == null)
                    {
                        // Item is not selected
                        if(glob_layout.equals("grid"))
                        {
                            tvName.setBackgroundColor(getResources().getColor(R.color.brightgrey));
                        } else {
                            view.setBackgroundResource(R.drawable.bkg_light);
                        }
                    } else {
                        // Item is selected
                        if(glob_layout.equals("grid"))
                        {
                            tvName.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                        } else {
                            view.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                        }
                    }

                    if (oslist.get(position).get("type").equals("image"))
                    {
                        String filename = oslist.get(position).get("filename");
                        String file_parent = oslist.get(position).get("parent");

                        String imgPath  = tmp_folder + Helper.md5(file_parent + filename) + ".jpg";
                        String thumbPath = tmp_folder + Helper.md5(file_parent + filename) + "_thumb.jpg";

                        if(new File(imgPath).exists())
                        {
                            Bitmap bmp = BitmapFactory.decodeFile(imgPath);
                            imgview.setImageBitmap(bmp);
                        } else if(new File(thumbPath).exists()) {
                            Bitmap bmp = BitmapFactory.decodeFile(thumbPath);
                            imgview.setImageBitmap(bmp);
                        } else {
                            HashMap<String, String> thumb = new HashMap<>();
                            thumb.put("file", allElements.get(position).toString());
                            thumb.put("filename", filename);
                            thumb.put("path", thumbPath);

                            if(!thumbQueue.contains(thumb))
                            {
                                thumbQueue.add(thumb);
                            }

                            if(imgLoader == null)
                            {
                                loadThumb();
                            }
                        }
                    }

                    imgview.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            if (selectedElem_map.size() > 0 || glob_layout.equals("list"))
                            {
                                toggleSelection(position);
                            } else {
                                openFile(position);
                            }
                        }
                    });

                    imgview.setOnLongClickListener(new View.OnLongClickListener()
                    {
                        @Override
                        public boolean onLongClick(View v)
                        {
                            longClicked = true;
                            toggleSelection(position);
                            return true;
                        }
                    });
                    return view;
                }
            };

			// Set current directory
            String dir = hierarchy.get(hierarchy.size() - 1).get("filename").toString();
            String title = (displayMode == 3) ? "Trash" : (dir.equals("")) ? "Homefolder" : dir;

            if(toolbar != null && uploadsPending != null) {
                toolbar.setTitle("Upload to: " + title);
            }
            else if(toolbar != null) {
                toolbar.setTitle(title);
            }

            list.setAdapter(adapter);
            unselectAll();

            if(uploadsPending == null) {
                bCreate.setVisibility(View.GONE);
            }
            bUpload.setVisibility(View.GONE);
			list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (selectedElem_map.size() > 0 && !longClicked) {
                        toggleSelection(position);
                    } else if (!longClicked) {
                        openFile(position);
                    }
                    longClicked = false;
                }
            });

            list.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    boolean enable2 = (list != null && list.getChildCount() > 0 && list.getFirstVisiblePosition() == 0 && list.getChildAt(0).getTop() == 0);
                    mSwipeRefreshLayout.setEnabled(enable2);
                }
            });
		} catch (JSONException e) {
			e.printStackTrace();
		}
   	}

    public void openFile(int position)
    {
        String type = oslist.get(+position).get("type");
        if(displayMode == 3)
        {
            return;
        } else if(type.equals("folder")) {
            hierarchy.add(allElements.get(position));
            new ListContent().execute();
        } else if(type.equals("image")) {
            Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
            i.putExtra("file", allElements.get(position).toString());
            i.putExtra("filename", oslist.get(position).get("filename"));
            i.putExtra("parent", oslist.get(position).get("parent"));
            e.startActivity(i);
        } else if(type.equals("audio") && mPlayerService != null) {
            Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
            audioFilename = oslist.get(position).get("filename");
            new GetLink().execute(position);
        } else {
            Toast.makeText(e, "Can not open file", Toast.LENGTH_SHORT).show();
        }
        unselectAll();
    }

    public void toggleSelection(int position)
    {
        if(selectedElem_map.get(position) == null)
        {
            // Select
            selectedElem_map.put(position, allElements.get(position));
        } else {
            // Unselect
            selectedElem_map.remove(position);
        }
        invalidateOptionsMenu();
        ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();
    }

    public void loadThumb()
    {
        if(thumbQueue.size() == 0)
        {
            return;
        }

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        String size = (glob_layout.equals("list")) ? Helper.dpToPx(100) + "" : Integer.toString(displaymetrics.widthPixels / 2);

        HashMap<String, String> thumb = thumbQueue.remove(0);

        imgLoader = new ImageLoader(new ImageLoader.TaskListener()
        {
            @Override
            public void onFinished(final Bitmap bmp)
            {
                if (bmp != null)
                {
                    // Update adapter to display thumb
                    ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();
                }
                if(thumbQueue.size() > 0)
                {
                    // Load next thumb
                    loadThumb();
                }
                else if(imgLoader != null) {
                    imgLoader.cancel(true);
                    imgLoader = null;
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            imgLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, thumb.get("file"), thumb.get("filename"), size, size, thumb.get("path"));
        }
        else {
            imgLoader.execute(thumb.get("file"), thumb.get("filename"), size, size, thumb.get("path"));
        }
    }

    public class GetLink extends AsyncTask<Integer, String, String>
    {
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Integer... info)
        {
            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("target", allElements.get(info[0]).toString());
            data.put("type",  "mobile_audio");
            data.put("filename", audioFilename);
            data.put("action", "cache");

            return server + Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            if(!value.equals("")) {
                mPlayerService.initPlay(value);
            }
            else {
                Toast.makeText(e, "Error playing audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class Connect extends AsyncTask<String, String, String> {
    	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            empty.setText("Connecting ...");
    	}
    	
    	@Override
        protected String doInBackground(String... login) {
            AccountManager accMan = AccountManager.get(RemoteFiles.this);
            Account[] sc = accMan.getAccountsByType("org.simpledrive");

            if (sc.length == 0) {
                return null;
            }

            username = sc[0].name;
            String url = server + "php/core_login.php";
            HashMap<String, String> data = new HashMap<>();
            data.put("user", username);
            data.put("pass", accMan.getPassword(sc[0]));

            return Connection.forString(url, data);
    	}
    	 @Override
         protected void onPostExecute(String value) {
             if(value != null && value.equals("1")) {
                 try {
                     hierarchy = new ArrayList<>();

                     JSONObject currDir = new JSONObject();
                     currDir.put("filename", "");
                     currDir.put("parent", "");
                     currDir.put("owner", username);
                     currDir.put("hash", 0);
                     currDir.put("rootshare", 0);
                     hierarchy.add(currDir);

                     empty.setText("Nothing to see here.");
                     new ListContent().execute();
                 }
                 catch (JSONException e) {
                     e.printStackTrace();
                 }
             }
             else {
                 Toast.makeText(e, "Error reconnecting", Toast.LENGTH_SHORT).show();
                 Intent i = new Intent(e.getApplicationContext(), Login.class);
                 e.startActivity(i);
                 e.finish();
             }
    	 }
    }

  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (requestCode == 1)
    {
      if (resultCode == RESULT_OK)
      {
          ArrayList<String> path = new ArrayList<>();
          path.add(data.getStringExtra("path"));
          upload_handler(path);
      }
    }
  }

  public void onBackPressed() {
      if(getSelectedElem_map().length() != 0) {
          unselectAll();
      }
      else if (hierarchy.size() > 1) {
        hierarchy.remove(hierarchy.size() - 1);
        new ListContent().execute();
      }
      else if(displayMode == 3) {
          displayMode = 0;
          new ListContent().execute();
      }
      else {
          super.onBackPressed();
      }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
  public boolean onContextItemSelected(MenuItem item) {
  	final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

  	switch(item.getItemId()) {
  		case 0:
  	    	showRename(oslist.get(info.position).get("filename"));
  	    	return true;

  		case 1:
  			new Delete().execute(info.position);
  	    	return true;

        case 2:
            showShare();
            return true;

        case 3:
            new Unshare().execute(info.position);
            return true;

  		case 4:
            new Zip().execute(info.position);
  			return true;

  		case 5:
            download();
            return true;

        case 6:
            new Restore().execute();
            return true;

  		default:
  			return super.onContextItemSelected(item);
  	}
  }

    private void download() {
        Download dl = new Download();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            dl.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        else {
            dl.execute();
        }
    }

    private void showShare() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(e);
        View shareView = View.inflate(this, R.layout.share_dialog, null);
        final EditText shareUser = (EditText) shareView.findViewById(R.id.shareUser);
        final CheckBox shareWrite = (CheckBox) shareView.findViewById(R.id.shareWrite);
        final CheckBox sharePublic = (CheckBox) shareView.findViewById(R.id.sharePublic);

        dialog.setTitle("Share")
                .setView(shareView)
                .setCancelable(true)
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        final AlertDialog dialog2 = dialog.create();
        dialog2.show();
        dialog2.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (shareUser.getText().toString().isEmpty() && !sharePublic.isChecked()) {
                    Toast.makeText(e, "Enter a username", Toast.LENGTH_SHORT).show();
                } else {
                    new Share(shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked()).execute();
                    dialog2.dismiss();
                }
            }
        });

        shareUser.requestFocus();
        showVirtualKeyboard();
    }
  
  private class NewFile extends AsyncTask<String, String, String> {
   	@Override
       protected void onPreExecute() {
        super.onPreExecute();
           e.setProgressBarIndeterminateVisibility(true);
   	}
   	
   	@Override
       protected String doInBackground(String... pos) {
   		String url = server + "php/files_api.php";
   		HashMap<String, String> data = new HashMap<>();

   		data.put("type", "folder");
   		data.put("filename", pos[0]);
   		data.put("action", "create");
        data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
        return Connection.forString(url, data);
   	}
   	 @Override
        protected void onPostExecute(String value) {
   		 if(value.length() == 0) {
   			new ListContent().execute();
   		 }
   		 else {
   			Toast.makeText(e, "Error creating folder", Toast.LENGTH_SHORT).show();
   		 }
   	 }
   }

    private void showCreate() {
        AlertDialog.Builder alert = new AlertDialog.Builder(e);

        alert.setTitle("New Folder");

        // Set an EditText view to get user input
        final EditText input = new EditText(e);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newFilename = input.getText().toString();
                new NewFile().execute(newFilename);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
        input.requestFocus();
        input.selectAll();
        showVirtualKeyboard();
    }
  
  private void showRename(final String filename) {
  	AlertDialog.Builder alert = new AlertDialog.Builder(e);

  	alert.setTitle("Rename");

  	// Set an EditText view to get user input 
  	final EditText input = new EditText(e);
  	alert.setView(input);
  	String fn_without_ext = (filename.lastIndexOf('.') != -1) ? filename.substring(0, filename.lastIndexOf('.')) : filename;
  	input.setText(fn_without_ext);

  	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
  	public void onClick(DialogInterface dialog, int whichButton) {
        String newFilename = input.getText().toString();
        new Rename().execute(newFilename);
  	  }
  	});

  	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
  	  public void onClick(DialogInterface dialog, int whichButton) {
  	    // Canceled.
  	  }
  	});

  	alert.show();
  	input.requestFocus();
  	input.selectAll();
  	showVirtualKeyboard();
  }
  
  private void showVirtualKeyboard() {
  	Timer timer = new Timer();
  	timer.schedule(new TimerTask()
    {
        @Override
        public void run()
        {
            InputMethodManager m = (InputMethodManager) e.getSystemService(Context.INPUT_METHOD_SERVICE);

            if (m != null)
            {
                m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
            }
        }
  	}, 100);
  }
  
  private class Rename extends AsyncTask<String, String, String> {
   	@Override
       protected void onPreExecute() {
           super.onPreExecute();
           e.setProgressBarIndeterminateVisibility(false);
   	}
   	
   	@Override
       protected String doInBackground(String... names) {
   		String url = server + "php/files_api.php";
   		HashMap<String, String> data = new HashMap<>();

   		data.put("action", "rename");
   		data.put("newFilename", names[0]);
        data.put("target", selectedElem_map.entrySet().iterator().next().getValue().toString());
        return Connection.forString(url, data);
   	}
   	 @Override
        protected void onPostExecute(String value) {
   		 if(value.length() == 0) {
   			new ListContent().execute();
   		 }
   		 else {
   			Toast.makeText(e, "Error renaming", Toast.LENGTH_SHORT).show();
   		 }
   	 }
   }
  
  private class Download extends AsyncTask<String, Integer, String> {
		private NotificationCompat.Builder mBuilder;
		private NotificationManager mNotifyManager;
		private int notificationId = 2;
		
   	@Override
      protected void onPreExecute() {
          super.onPreExecute();
          
          Intent intent = new Intent(e, RemoteFiles.class);
          PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);
          
          mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
          mBuilder = new NotificationCompat.Builder(e);
          mBuilder.setContentTitle("Downloading...")
          	.setContentText("Download in progress")
          	.setContentIntent(pIntent)
          	.setOngoing(true)
          	.setSmallIcon(R.drawable.cloud_icon_notif);
          mBuilder.setProgress(100, 0, false);
          mNotifyManager.notify(notificationId, mBuilder.build());
   	}
   	
   	@Override
    protected String doInBackground(String... names)
    {
  		String url = server + "php/files_api.php";
  		
   		HashMap<String, String> data = new HashMap<>();

   		data.put("action", "download");
   		data.put("source", getSelectedElem_map().toString());
        data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
   		
        new simpledrive.lib.Download(new DownloadListener()
        {
            @Override
            public void transferred(Integer num)
            {
                if(num % 5 == 0)
                {
                    publishProgress(num);
                }
            }
        });
			
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        return simpledrive.lib.Download.download(url, data, path);
   	}
   	
      @Override
      protected void onProgressUpdate(Integer... values)
      {
          super.onProgressUpdate(values);
          mBuilder.setProgress(100, values[0], false);
          mNotifyManager.notify(notificationId, mBuilder.build());

          if(values[0] == 100)
          {
          	mBuilder.setContentTitle("Download complete")
          	.setOngoing(false)
          	.setContentText("")
          	.setProgress(0,0,false);
          	mNotifyManager.notify(notificationId, mBuilder.build());
          }
      }
   	
  	 @Override
       protected void onPostExecute(String value) {
  		// Do something
  	 }
  }

    private JSONArray getSelectedElem_map() {
        JSONArray arr = new JSONArray();
        for(Map.Entry<Integer, JSONObject> entry : selectedElem_map.entrySet()) {
            arr.put(entry.getValue());
        }
        return arr;
    }
  
  private class Delete extends AsyncTask<Integer, String, String> {
   	@Override
       protected void onPreExecute() {
           super.onPreExecute();
           e.setProgressBarIndeterminateVisibility(true);
   	}
   	
   	@Override
       protected String doInBackground(Integer... pos) {
   		String url = server + "php/files_api.php";
   		HashMap<String, String> data = new HashMap<>();

   		data.put("action", "delete");
   		data.put("final", Boolean.toString(displayMode == 3));
        data.put("source", getSelectedElem_map().toString());
        data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
        return Connection.forString(url, data);
   	}
   	 @Override
        protected void onPostExecute(String value) {
           e.setProgressBarIndeterminateVisibility(false);
   		 if(value.length() == 0) {
   			new ListContent().execute();
   		 }
   		 else {
   			Toast.makeText(e, "Error deleting", Toast.LENGTH_SHORT).show();
   		 } 
   	 }
   }

    private class Share extends AsyncTask<Void, String, String> {
        String shareUser;
        int shareWrite;
        int sharePublic;

        public Share(String shareUser, boolean shareWrite, boolean sharePublic) {
            this.shareUser = shareUser;
            this.shareWrite = (shareWrite) ? 1 : 0;
            this.sharePublic = (sharePublic) ? 1 : 0;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected String doInBackground(Void... params) {
            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("mail", "");
            data.put("key", "");
            data.put("userto", shareUser);
            data.put("pubAcc", Integer.toString(sharePublic));
            data.put("write", Integer.toString(shareWrite));
            data.put("action", "share");
            data.put("target", selectedElem_map.entrySet().iterator().next().getValue().toString());
            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(final String value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.length() != 0) {
                new ListContent().execute();
                if(this.sharePublic == 1) {
                    final AlertDialog.Builder dialog = new AlertDialog.Builder(e);

                    dialog.setMessage("Send link?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String htmlBody = username + " wants to share a file with you.<br>Access it via the following link:<br><br>" + value;
                            Spanned shareBody = Html.fromHtml(htmlBody);
                            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "simpleDrive share link");
                            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
                            startActivity(Intent.createChooser(sharingIntent, "Send via"));
                        }
                    }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("label", value);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(e, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                        }
                    }).show();
                }
            }
            else {
                Toast.makeText(e, "Error sharing", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Unshare extends AsyncTask<Integer, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected String doInBackground(Integer... pos) {
            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "unshare");
            data.put("target", selectedElem_map.entrySet().iterator().next().getValue().toString());
            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.length() != 0) {
                Toast.makeText(e, value, Toast.LENGTH_SHORT).show();
            }
            else {
                new ListContent().execute();
            }
        }
    }

    private class Zip extends AsyncTask<Integer, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected String doInBackground(Integer... pos) {
            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("action", "zip");
            data.put("source", getSelectedElem_map().toString());
            data.put("target", hierarchy.get(hierarchy.size() - 1).toString());
            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(!value.isEmpty()) {
                Toast.makeText(e, "Error zipping", Toast.LENGTH_SHORT).show();
            }
            else {
                new ListContent().execute();
            }
        }
    }

    private class Restore extends AsyncTask<Integer, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected String doInBackground(Integer... pos) {

            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<>();

            data.put("test", "test");
            data.put("trash", "true");
            data.put("action", "move");
            data.put("source", getSelectedElem_map().toString());
            data.put("target", hierarchy.get(0).toString());

            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.isEmpty()) {
                new ListContent().execute();
            }
            else {
                Toast.makeText(e, "Error restoring", Toast.LENGTH_SHORT).show();
            }
        }
    }
  
  private class Upload extends AsyncTask<String, Integer, String> {
			private NotificationCompat.Builder mBuilder;
			private NotificationManager mNotifyManager;
			private int notificationId = 1;
			String filename;
      
   	@Override
       protected void onPreExecute() {
           super.onPreExecute();
           
           Intent intent = new Intent(e, RemoteFiles.class);
           PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);
           
           mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
           mBuilder = new NotificationCompat.Builder(e);
           mBuilder.setContentTitle("Uploading...")
                   .setContentText("Upload in progress")
                   .setContentIntent(pIntent)
                   .setOngoing(true)
                   .setSmallIcon(R.drawable.cloud_icon_notif);
           mBuilder.setProgress(100, 0, false);
           mNotifyManager.notify(notificationId, mBuilder.build());

            upload_current++;
   	}
   	
   	@Override
       protected String doInBackground(String... path) {
        HashMap<String, String> ul_elem = upload_queue.remove(0);
        filename = ul_elem.get("filename");
        String filepath = ul_elem.get("path");
        String relative = ul_elem.get("relative");
        String target = ul_elem.get("target");

   		String url = server + "php/files_api.php";
  		
   		simpledrive.lib.Upload myEntity = new simpledrive.lib.Upload(new ProgressListener()
				{
					@Override
					public void transferred(Integer num)
					{
						if(num % 5 == 0) {
							publishProgress(num);
						}
					}
				});
        return simpledrive.lib.Upload.upload(myEntity, url, filepath, relative, target);
   	}
   	
      @Override
      protected void onProgressUpdate(Integer... values) {
          super.onProgressUpdate(values);
          mBuilder.setProgress(100, values[0], false).setContentTitle("Uploading " + upload_current + " of " + upload_total).setContentText(filename);
          mNotifyManager.notify(notificationId, mBuilder.build());
      }
      
   	 @Override
        protected void onPostExecute(String value) {
         upload_successful = (value.length() > 0) ? upload_successful : upload_successful + 1;
         if(upload_queue.size() > 0) {
             new Upload().execute();
         }
         else {
             String file = (upload_total == 1) ? "file" : "files";
             mBuilder.setContentTitle("Upload complete")
                     .setContentText(upload_successful + " of " + upload_total + " " + file + " added")
                     .setOngoing(false)
                     .setProgress(0, 0, false);
             mNotifyManager.notify(notificationId, mBuilder.build());
             uploading = false;
             if(uploadsPending == null) {
                 new ListContent().execute();
             }
         }
   	 }
   }

    public void upload_add_recursive(String orig_path, File dir, String target) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                upload_add_recursive(orig_path, file, target);
            } else {
                String rel_dir = file.getParent().substring(orig_path.length()) + "/";
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", rel_dir);
                ul_elem.put("path", file.getPath());
                ul_elem.put("target", target);
                upload_queue.add(ul_elem);
                upload_total++;
            }
        }
    }

    public void upload_handler(ArrayList<String> paths)
    {
        for(String path : paths)
        {
            File file = new File(path);
            upload_add_recursive(file.getParent(), file, hierarchy.get(hierarchy.size() - 1).toString());
            if(file.isDirectory())
            {
                upload_add_recursive(file.getParent(), file, hierarchy.get(hierarchy.size() - 1).toString());
            } else {
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", "");
                ul_elem.put("path", path);
                ul_elem.put("target", hierarchy.get(hierarchy.size() - 1).toString());
                upload_queue.add(ul_elem);
                upload_total++;
            }
        }

        if(!uploading) {
            new Upload().execute();
        }
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) {
            result = contentURI.getPath();
        }
        else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private ArrayList<String> isSendIntent(Intent intent)
    {
        String action = intent.getAction();
        String type = intent.getType();
        ArrayList<String> uploads = new ArrayList<>();

        if (Intent.ACTION_SEND.equals(action) && type != null)
        {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type.startsWith("image/"))
            {
                uploads.add(getRealPathFromURI(uri));
            } else {
                uploads.add(uri.getPath());
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            for(Uri uri : uris) {
                if (uri.toString().startsWith("content"))
                {
                    uploads.add(getRealPathFromURI(uri));
                } else {
                    uploads.add(uri.getPath());
                }
            }
        } else {
            return null;
        }
        return uploads;
    }

    protected void onCreate(Bundle paramBundle)
    {
        super.onCreate(paramBundle);

        e = this;

        setContentView(R.layout.remote_files);

        myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
        empty = (TextView) findViewById(R.id.empty_list_item);
        empty.setTypeface(myTypeface);

        uploadsPending = isSendIntent(getIntent());

        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        bUpload = ((ImageButton) findViewById(R.id.bUpload));
        bCreate = ((ImageButton) findViewById(R.id.bCreate));
        final ImageButton toggleButton = ((ImageButton) findViewById(R.id.bAdd));
        final ImageButton bOK = ((ImageButton) findViewById(R.id.bOK));

        bUpload.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bCreate.setVisibility(View.GONE);
                bUpload.setVisibility(View.GONE);
                Intent result = new Intent(RemoteFiles.this, LocalFiles.class);
                startActivityForResult(result, 1);
            }
        });

        bCreate.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                showCreate();
            }
        });

        toggleButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (bCreate.getVisibility() == View.GONE)
                {
                    bCreate.setVisibility(View.VISIBLE);
                    bUpload.setVisibility(View.VISIBLE);
                } else {
                    bCreate.setVisibility(View.GONE);
                    bUpload.setVisibility(View.GONE);
                }
            }
        });

        bOK.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                upload_handler(uploadsPending);
                e.finish();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            toggleButton.setBackgroundResource(R.drawable.action_button_ripple);
            bUpload.setBackgroundResource(R.drawable.action_button_ripple);
            bCreate.setBackgroundResource(R.drawable.action_button_ripple);
            bOK.setBackgroundResource(R.drawable.action_button_ripple);
        }

        if(uploadsPending != null)
        {
            bOK.setVisibility(View.VISIBLE);
            bCreate.setVisibility(View.VISIBLE);
        } else {
            toggleButton.setVisibility(View.VISIBLE);
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                new ListContent().execute();
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.darkgreen, R.color.darkgreen, R.color.darkgreen, R.color.darkgreen);
        mSwipeRefreshLayout.setProgressViewOffset(false, Helper.dpToPx(56), Helper.dpToPx(56) + 100);

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        server = settings.getString("server", "");
        glob_layout = (settings.getString("view", "").length() == 0) ? "list" : settings.getString("view", "");
        setView(glob_layout);

        // Create image cache folder
        File tmp = new File(tmp_folder);
        if (!tmp.exists())
        {
            tmp.mkdir();
        }

        toolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(toolbar);

        new Connect().execute();
    }

    public void setView(String view)
    {
        glob_layout = view;
        settings.edit().putString("view", glob_layout).commit();

        if(glob_layout.equals("list"))
        {
            list = (ListView) findViewById(R.id.list);
            GridView tmp_grid = (GridView) findViewById(R.id.grid);
            tmp_grid.setVisibility(View.GONE);
        } else {
            list = (GridView) findViewById(R.id.grid);
            ListView tmp_list = (ListView) findViewById(R.id.list);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);
        if(uploadsPending == null)
        {
            registerForContextMenu(list);
        }
    }

  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
  {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

    if(selectedElem_map.get(info.position) == null)
    {
        unselectAll();
    }

    selectedElem_map.put(info.position, allElements.get(info.position));
    ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();

    menu.add(0, 1, 1, "Delete");

    if(!(displayMode == 3))
    {
        if(getSelectedElem_map().length() == 1)
        {
            menu.add(0, 0, 0, "Rename");

            if (oslist.get(info.position).get("hash").equals("null") && oslist.get(info.position).get("owner").equals(username))
            {
                menu.add(0, 2, 2, "Share");
            } else if (!oslist.get(info.position).get("hash").equals("null")) {
                menu.add(0, 3, 3, "Unshare");
            }
        }

        menu.add(0, 4, 4, "Zip");
        menu.add(0, 5, 5, "Download");
    } else {
        menu.add(0, 6, 6, "Restore");
    }
    menu.setHeaderTitle("Options");
  }

  protected void onDestroy()
  {
    super.onDestroy();
    if (mBound && mPlayerService.isPlaying())
    {
      Intent localIntent = new Intent(this, RemoteFiles.class);
      localIntent.setAction("org.simpledrive.action.startbackground");
        startService(localIntent);
    } else if (mBound) {
        getApplicationContext().unbindService(mServiceConnection);
    }
  }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if(uploadsPending != null)
        {
            return false;
        }

        if(glob_layout.equals("list"))
        {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_grid);
        } else {
            menu.findItem(R.id.toggle_view).setIcon(R.drawable.ic_list);
        }

        if(selectedElem_map.size() > 0)
        {
            menu.findItem(R.id.toggle_view).setVisible(false);
            menu.findItem(R.id.delete).setVisible(true);
            menu.findItem(R.id.download).setVisible(true);
            if(selectedElem_map.size() == 1)
            {
                menu.findItem(R.id.share).setVisible(true);
            }
            else {
                menu.findItem(R.id.share).setVisible(false);
            }
        }
        else {
            menu.findItem(R.id.toggle_view).setVisible(true);
            menu.findItem(R.id.delete).setVisible(false);
            menu.findItem(R.id.share).setVisible(false);
            menu.findItem(R.id.download).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onCreateOptionsMenu(Menu menu)
    {
        if(uploadsPending == null)
        {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main, menu);
        }
        return true;
    }

  public boolean onOptionsItemSelected(MenuItem paramMenuItem)
  {
    switch (paramMenuItem.getItemId())
    {
        default:
            return super.onOptionsItemSelected(paramMenuItem);

        case R.id.logout:
            Connection.logout(e);
            startActivity(new Intent(getApplicationContext(), org.simpledrive.Login.class));
            finish();
            break;

        case R.id.trash:
            openTrash();
            break;

        case R.id.toggle_view:
            String next_view = (glob_layout.equals("grid")) ? "list" : "grid";
            setView(next_view);
            new ListContent().execute();
            break;

        case R.id.delete:
            new Delete().execute();
            break;

        case R.id.download:
            download();
            break;

        case R.id.share:
            showShare();
            break;
    }

    return true;
  }

    public void openTrash()
    {
        displayMode = 3;
        JSONObject first = hierarchy.get(0);
        hierarchy = new ArrayList<>();
        hierarchy.add(first);
        new ListContent().execute();
    }
}