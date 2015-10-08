package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Activity;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
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

public class RemoteFiles extends Activity
{
    public static RemoteFiles e;
    public static String audioFilename;
    public static String server;
    public static long currentRequest = 0;
    private static Typeface myTypeface;
    private static AbsListView list;
    private static ArrayList<HashMap<String, String>> oslist;
    private static JSONObject currDir = new JSONObject();
    private static HashMap<Integer, JSONObject> allElements = new HashMap<Integer, JSONObject>();
    private static HashMap<Integer, JSONObject> selectedElem_map= new HashMap<Integer, JSONObject>();
    private static ArrayList hierarchy = new ArrayList();
    private static String username = "";
    private static AudioService mPlayerService;
    private boolean mBound = false;
    private static int displayMode = 0;
    private String tmp_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    private TextView empty;
    private static String glob_layout;
    private SharedPreferences settings;
    private boolean longClicked = false;
  
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
            pDialog.setCancelable(true);
            pDialog.show();
    	}
    	
    	@Override
        protected JSONArray doInBackground(String... args) {
    		String url = server + "php/files_api.php";
    		HashMap<String, String> data = new HashMap<String, String>();

    		data.put("target", currDir.toString());
    		data.put("mode", Integer.toString(displayMode));
            data.put("action", "list");

            return Connection.forJSON(url, data);
    	}
    	 @Override
         protected void onPostExecute(JSONArray value) {
             pDialog.dismiss();
    		 listContent(value);
    	 }
    }

    private static void unselectAll() {
        selectedElem_map = new HashMap<Integer, JSONObject>();
        ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();
    }
  
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void listContent(final JSONArray json) {
        try {
            if(json == null || (json.length() != 0 && json.getJSONObject(0).has("error"))) {
                new Connect().execute();
                return;
            }

            currentRequest = System.currentTimeMillis();

            oslist = new ArrayList<HashMap<String, String>>();

            for(int i = 0; i < json.length(); i++){
                JSONObject c = json.getJSONObject(i);
                HashMap<String, String> map = new HashMap<String, String>();

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

            List<HashMap<String, String>> listinfo = new ArrayList<HashMap<String, String>>();
            listinfo.clear();
		     

            for(int i = 0; i < oslist.size(); i++)
            {
                 HashMap<String, String> hm = new HashMap<String, String>();
                 if(displayMode == 3) {
                     int pos = oslist.get(i).get("filename").lastIndexOf("_trash");
                     hm.put("filename", oslist.get(i).get("filename").substring(0, pos));
                 }
                 else {
                     hm.put("filename", oslist.get(i).get("filename"));
                 }

                 if(oslist.get(i).get("type").equals("folder")) {
                	 hm.put("image", Integer.toString(image[0]));
                 }
                 else if(oslist.get(i).get("type").equals("audio")) {
                	 hm.put("image", Integer.toString(image[2]));
                 }
                 else if(oslist.get(i).get("type").equals("pdf")) {
                	 hm.put("image", Integer.toString(image[3]));
                 }
                 else if(oslist.get(i).get("type").equals("image")) {
                	 hm.put("image", Integer.toString(image[4]));
                 }
                 else {
                	 hm.put("image", Integer.toString(image[1]));
                 }

                 if(oslist.get(i).get("type").equals("folder")) {
                     hm.put("size", "");
                 }
                 else {
                     hm.put("size", oslist.get(i).get("size"));
                 }

                 if(!oslist.get(i).get("owner").equals(username)) {
                	 hm.put("owner", oslist.get(i).get("owner"));
                 }
                 else if(!oslist.get(i).get("closehash").equals("null")) {
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
                    final ImageView imgview = (ImageView) view.findViewById(R.id.icon);

                    tvName.setTypeface(myTypeface);
                    TextView tvType = (TextView) view.findViewById(R.id.size);
                    tvType.setTypeface(myTypeface);

                    if (!oslist.get(position).get("type").equals("image") && glob_layout.equals("grid"))
                    {
                        imgview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) imgview.getLayoutParams();
                        lp.height = 200;
                        lp.width = 200;
                        imgview.setLayoutParams(lp);
                    } else if (glob_layout.equals("grid")) {
                        imgview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) imgview.getLayoutParams();
                        lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                        lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                        imgview.setLayoutParams(lp);
                    }

                    if (oslist.get(position).get("type").equals("folder") || glob_layout.equals("grid"))
                    {
                        tvName.setGravity(Gravity.CENTER_VERTICAL);
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) tvName.getLayoutParams();
                        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                        tvName.setLayoutParams(layoutParams);
                    } else {
                        tvName.setGravity(Gravity.TOP);
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) tvName.getLayoutParams();
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                        tvName.setLayoutParams(layoutParams);
                    }

                    if (selectedElem_map.get(position) == null)
                    {
                        // Item is not selected
                        if(glob_layout.equals("grid")) {
                            tvName.setBackgroundColor(getResources().getColor(R.color.brightgrey));
                        }
                        else {
                            view.setBackgroundResource(R.drawable.bkg_light);
                        }
                    } else {
                        // Item is selected
                        if(glob_layout.equals("grid")) {
                            tvName.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                        }
                        else {
                            view.setBackgroundColor(getResources().getColor(R.color.lightgreen));
                        }
                    }

                    if (oslist.get(position).get("type").equals("image"))
                    {
                        try
                        {
                            JSONObject file = allElements.get(position);
                            String filename = file.get("filename").toString();
                            String file_parent = file.get("parent").toString();
                            //String cachePath = tmp_folder + Helper.md5(file_parent + filename) + "_thumb.jpg";

                            String thumbPath = tmp_folder + Helper.md5(file_parent + filename) + "_thumb.jpg";
                            String imgPath  = tmp_folder + Helper.md5(file_parent + filename) + ".jpg";

                            File thumb = new File(thumbPath);
                            File img = new File(imgPath);

                            if(img.exists()) {
                                //Log.i("using", "existing");
                                Bitmap bmp = BitmapFactory.decodeFile(imgPath);
                                imgview.setImageBitmap(bmp);
                            }
                            else if(thumb.exists()) {
                                Bitmap bmp = BitmapFactory.decodeFile(thumbPath);
                                imgview.setImageBitmap(bmp);
                            }
                            else {
                                //Log.i("loading thumb for", oslist.get(position).get("filename"));
                                loadThumb(currentRequest, file.toString(), filename, thumbPath);
                            }
                        } catch (JSONException e1) {
                            e1.printStackTrace();
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
			String dir = currDir.getString("filename");
            String title = (displayMode == 3) ? "Trash" : (dir.equals("")) ? "Homefolder" : dir;
			e.getActionBar().setTitle(title);

            list.setAdapter(adapter);
            unselectAll();
			list.setOnItemClickListener(new AdapterView.OnItemClickListener()
			{
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
            currDir = allElements.get(position);
            hierarchy.add(currDir);
            new ListContent().execute();
        } else if(type.equals("image")) {
            try
            {
                //selectedElem = allElem.getJSONObject(position);

                JSONObject obj = allElements.get(position);
                Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
                i.putExtra("file", obj.toString());
                i.putExtra("filename", obj.get("filename").toString());
                i.putExtra("parent", obj.get("parent").toString());
                e.startActivity(i);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "Error opening image", Toast.LENGTH_SHORT).show();
            }
        } else if(type.equals("audio")) {
            /*try
            {
                selectedElem = allElem.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }*/

            if(mPlayerService != null)
            {
                Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
                audioFilename = oslist.get(position).get("filename");
                new GetLink().execute(position);
            }
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
        ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();
    }

    public void loadThumb(final long cR, String file,String filename, String path)
    {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        String size = (glob_layout.equals("list")) ? Helper.dpToPx(100) + "" : Integer.toString(displaymetrics.widthPixels / 2);

        ImageLoader task = new ImageLoader(false, new ImageLoader.TaskListener()
        {
            @Override
            public void onFinished(final Bitmap bmp)
            {
                if (bmp != null && cR == currentRequest)
                {
                    // Update adapter
                    ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();
                }
            }
        });
        task.execute(file, filename, size, size, path);
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
            HashMap<String, String> data = new HashMap<String, String>();

            data.put("target", allElements.get(info[0]).toString()); //.entrySet().iterator().next().toString());
            //data.put("target", selectedElem_map.get(selected).toString()); //selectedElem.toString());
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
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("user", username);
            data.put("pass", accMan.getPassword(sc[0]));

            return Connection.forString(url, data);
    	}
    	 @Override
         protected void onPostExecute(String value) {
             if(value != null && value.equals("1")) {
                 try {
                     hierarchy = new ArrayList();

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
          String upload_path = data.getStringExtra("path");
          File file = new File(upload_path);
          if(file.isDirectory()) {
              upload_recursive(file.getParent(), file);
          }
          else {
              String filename = file.getName();
              new Upload().execute(data.getStringExtra("path"), "", filename);
          }
      }
    }
  }

    public void upload_recursive(String orig_path, File dir) {
        if(dir.exists()) {
            File[] files = dir.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    upload_recursive(orig_path, file);
                } else {
                    String rel_dir = file.getParent().substring(orig_path.length()) + "/";
                    new Upload().execute(file.getPath(), rel_dir, file.getName());
                }
            }
        }
    }

  public void onBackPressed() {
      if(getSelectedElem_map().length() != 0) {
          unselectAll();
      }
      else if (hierarchy.size() > 1) {
        hierarchy.remove(hierarchy.size() - 1);
        currDir = (JSONObject) hierarchy.get(hierarchy.size() - 1);
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
  			String filename1 = oslist.get(info.position).get("filename");
  			Download dl = new Download();
  		    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
  		        dl.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, filename1);
  		      }
  		      else {
  		        dl.execute(filename1);
  		      }
  			return true;

        case 6:
            new Restore().execute(info.position);
            return true;

  		default:
  			return super.onContextItemSelected(item);
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
                if(shareUser.getText().toString().isEmpty() && !sharePublic.isChecked()) {
                    Toast.makeText(e, "Enter a username", Toast.LENGTH_SHORT).show();
                }
                else {
                    new Share(shareUser.getText().toString(), shareWrite.isChecked(), sharePublic.isChecked()).execute();
                    dialog2.dismiss();
                }
            }
        });

        shareUser.requestFocus();
        showVirturalKeyboard();
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
   		HashMap<String, String> data = new HashMap<String, String>();

   		data.put("type", pos[0]);
   		data.put("filename", "");
   		data.put("action", "create");
   		data.put("target", currDir.toString());
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

  		String[] data = {newFilename};
  		new Rename().execute(data);
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
  	showVirturalKeyboard();
  }
  
  private void showVirturalKeyboard() {
  	Timer timer = new Timer();
  	timer.schedule(new TimerTask() {

	    	@Override
	    	public void run() {
	    		InputMethodManager m = (InputMethodManager) e.getSystemService(Context.INPUT_METHOD_SERVICE);
	
	    		if (m != null) {
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
   		HashMap<String, String> data = new HashMap<String, String>();

   		data.put("action", "rename");
   		data.put("newFilename", names[0]);
        data.put("target", selectedElem_map.entrySet().iterator().next().toString());
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
      protected String doInBackground(String... names) {
  		String url = server + "php/files_api.php";
  		
   		HashMap<String, String> data = new HashMap<String, String>();

   		data.put("action", "download");
   		data.put("source", getSelectedElem_map().toString());
        data.put("target", currDir.toString());
   		
			new simpledrive.lib.Download(new DownloadListener()
			{
				@Override
				public void transferred(Integer num)
				{
					if(num % 5 == 0) {
						publishProgress(num);
					}
				}
			});
			
			String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        return simpledrive.lib.Download.download(url, data, path);
   	}
   	
      @Override
      protected void onProgressUpdate(Integer... values) {
          super.onProgressUpdate(values);
          mBuilder.setProgress(100, values[0], false);
          mNotifyManager.notify(notificationId, mBuilder.build());
          if(values[0] == 100) {
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
   		HashMap<String, String> data = new HashMap<String, String>();

   		data.put("action", "delete");
   		data.put("final", Boolean.toString(displayMode == 3));
        data.put("source", getSelectedElem_map().toString());
        data.put("target", currDir.toString());
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
            HashMap<String, String> data = new HashMap<String, String>();

            //JSONArray test = new JSONArray();
            //test.put(selectedElem);

            data.put("mail", "");
            data.put("key", "");
            data.put("userto", shareUser);
            data.put("pubAcc", Integer.toString(sharePublic));
            data.put("write", Integer.toString(shareWrite));
            data.put("action", "share");
            //data.put("target", selectedElem.toString());
            data.put("target", selectedElem_map.entrySet().iterator().next().toString());
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
            HashMap<String, String> data = new HashMap<String, String>();

            //JSONArray test = new JSONArray();
            //test.put(selectedElem);
            data.put("action", "unshare");
            //data.put("target", selectedElem.toString());
            data.put("target", selectedElem_map.entrySet().iterator().next().toString());
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
            HashMap<String, String> data = new HashMap<String, String>();

            data.put("action", "zip");
            data.put("source", getSelectedElem_map().toString());
            data.put("target", currDir.toString());
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
            HashMap<String, String> data = new HashMap<String, String>();

            //JSONArray test = new JSONArray();
            //test.put(selectedElem);

            data.put("test", "test");
            data.put("trash", "true");
            data.put("action", "move");
            //data.put("source", test.toString());
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
   	}
   	
   	@Override
       protected String doInBackground(String... path) {
   		filename = path[2];
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

        return simpledrive.lib.Upload.upload(myEntity, url, path[0], path[1], currDir.toString());
   	}
   	
      @Override
      protected void onProgressUpdate(Integer... values) {
          super.onProgressUpdate(values);
          mBuilder.setProgress(100, values[0], false).setContentText(values[0] + "% | " + filename);
          mNotifyManager.notify(notificationId, mBuilder.build());
          if(values[0] == 100) {
          	mBuilder.setContentTitle("Upload complete")
                    .setContentText("File: " + filename)
                    .setOngoing(false)
          	.setProgress(0,0,false);
          	mNotifyManager.notify(notificationId, mBuilder.build());
          }
      }
      
   	 @Override
        protected void onPostExecute(String value) {
         if(value.length() > 0) {
             Toast.makeText(e, value, Toast.LENGTH_SHORT).show();
         }
         else {
             new ListContent().execute();
         }
   	 }
   }

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        e = this;

        setContentView(R.layout.remote_files);

        myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
        empty = (TextView) findViewById(R.id.empty_list_item);
        empty.setTypeface(myTypeface);

        Intent intent = new Intent();
        intent.setClass(this, AudioService.class);
        getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        final Button bUpload = ((Button) findViewById(R.id.bUpload));
        final Button bCreate = ((Button) findViewById(R.id.bCreate));
        final Button toggleButton = ((Button) findViewById(R.id.bAdd));

        bUpload.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                bCreate.setVisibility(View.GONE);
                bUpload.setVisibility(View.GONE);
                Intent result = new Intent(RemoteFiles.this, LocalFiles.class);
                startActivityForResult(result, 1);
            }
        });

        bCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new NewFile().execute("folder");
            }
        });

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bCreate.getVisibility() == View.GONE) {
                    bCreate.setVisibility(View.VISIBLE);
                    bUpload.setVisibility(View.VISIBLE);
                } else {
                    bCreate.setVisibility(View.GONE);
                    bUpload.setVisibility(View.GONE);
                }
            }
        });

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
        server = settings.getString("server", "");
        glob_layout = (settings.getString("view", "").length() == 0) ? "list" : settings.getString("view", "");
        setView(glob_layout);

        // Create image cache folder
        File tmp = new File(tmp_folder);
        if (!tmp.exists()) {
            tmp.mkdir();
        }

        new Connect().execute();
    }

    public void setView(String view) {
        glob_layout = view;
        settings.edit().putString("view", glob_layout).commit();

        if(glob_layout.equals("list")) {
            list = (ListView) findViewById(R.id.list);
            GridView tmp_grid = (GridView) findViewById(R.id.grid);
            tmp_grid.setVisibility(View.GONE);
        }
        else {
            list = (GridView) findViewById(R.id.grid);
            ListView tmp_list = (ListView) findViewById(R.id.list);
            tmp_list.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);
        registerForContextMenu(list);
    }

  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        if(selectedElem_map.get(info.position) == null) {
            unselectAll();
        }
        selectedElem_map.put(info.position, allElements.get(info.position));
      ((SimpleAdapter)list.getAdapter()).notifyDataSetChanged();

    menu.add(0, 1, 1, "Delete");

    if(!(displayMode == 3)) {
        if(getSelectedElem_map().length() == 1) {
            menu.add(0, 0, 0, "Rename");

            if (oslist.get(info.position).get("hash").equals("null") && oslist.get(info.position).get("owner").equals(username)) {
                menu.add(0, 2, 2, "Share");
            } else if (!oslist.get(info.position).get("hash").equals("null")) {
                menu.add(0, 3, 3, "Unshare");
            }
        }

        menu.add(0, 4, 4, "Zip");
        menu.add(0, 5, 5, "Download");
    }
    else {
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
    }
      else if (mBound) {
        getApplicationContext().unbindService(mServiceConnection);
    }
  }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

  public boolean onOptionsItemSelected(MenuItem paramMenuItem)
  {
    switch (paramMenuItem.getItemId()) {
        default:
            return super.onOptionsItemSelected(paramMenuItem);

        case R.id.logout:
            Connection.logout(e);
            startActivity(new Intent(getApplicationContext(), org.simpledrive.Login.class));
            finish();
            break;

        case R.id.trash:
            displayMode = 3;
            currDir = (JSONObject) hierarchy.get(0);
            new ListContent().execute();
            break;

        case R.id.changeview:
            String new_view = (glob_layout.equals("grid")) ? "list" : "grid";
            setView(new_view);
            new ListContent().execute();
    }

    return true;
  }
}