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
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.Spanned;
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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
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
import java.util.Timer;
import java.util.TimerTask;

import simpledrive.library.AudioService;
import simpledrive.library.AudioService.LocalBinder;
import simpledrive.library.Connection;
import simpledrive.library.DownloadFile;
import simpledrive.library.DownloadFile.DownloadListener;
import simpledrive.library.UploadFile;
import simpledrive.library.UploadFile.ProgressListener;

public class RemoteFiles extends Activity
{
  static Typeface myTypeface;
  public static RemoteFiles e;
  public static String audioFilename;
  static ListView list;
  static ArrayList<HashMap<String, String>> oslist;
  public static String server = "http";
  static String firstrun;
  static JSONObject currDir = new JSONObject();
  static JSONObject selectedElem = new JSONObject();
  static JSONArray allElem = new JSONArray();
  static JSONArray selectedElem2 = new JSONArray();
  static ArrayList hierarchy = new ArrayList();
  static AccountManager accMan;
  static Account[] sc;
  private static String username = "";
  private static AudioService mPlayerService;
  Button bCreate;
  Button toggleButton;
  Button bUpload;
  SharedPreferences settings;
  private boolean mBound = false;
  static int displayMode = 0;
  
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

  protected static String convertSize(String sSize) {
  	float size = Integer.parseInt(sSize);
  	String convSize;
  	
  	if(size > 1073741824) {
  		convSize = Math.floor((size / 1073741824) * 100) / 100 + " GB";
  	}
  	else if(size > 1048576) {
  		convSize = Math.floor((size / 1048576) * 100) / 100 + " MB";
  	}
  	else if(size > 1024) {
  		convSize = Math.floor((size / 1024) * 100) / 100 + " KB";
  	}
  	else {
  		convSize = size + " Byte";
  	}
  	return convSize;
  }

  private static class ListContent extends AsyncTask<String, String, JSONArray> {
	  private ProgressDialog pDialog;
    	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(e);
            pDialog.setMessage("Loading files ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
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

    private static void cancelSelections() {
        selectedElem2 = new JSONArray();
        for(int i = 0; i < allElem.length(); i++) {
            if (list.getChildAt(i) != null) {
                list.getChildAt(i).setBackgroundResource(R.drawable.bkg_light);
            }
            JSONObject obj = new JSONObject();
            try {
                selectedElem2.put(i, obj);
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
    }
  
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void listContent(final JSONArray json) {
        try {
            if(json == null || (json.length() != 0 && json.getJSONObject(0).has("error"))) {
                new Login().execute(sc[0].name, accMan.getPassword(sc[0]));
                Toast.makeText(e, "Reconnecting...", Toast.LENGTH_SHORT).show();
                return;
            }

            allElem = json;

            oslist = new ArrayList<HashMap<String, String>>();

            for(int i = 0; i < json.length(); i++){
                JSONObject c = json.getJSONObject(i);
                HashMap<String, String> map = new HashMap<String, String>();

                Iterator<String> it = c.keys();
                while (it.hasNext()) {
                    String n = it.next();
                    String size = (n.equals("size")) ? convertSize(c.getString(n)) : c.getString(n);
                    map.put(n, size);
                }
                oslist.add(map);
            }
			 
		     int[] image = new int[] { R.drawable.folder_thumb, R.drawable.unknown_thumb, R.drawable.audio_thumb, R.drawable.pdf_thumb, R.drawable.dirup, R.drawable.image_thumb};

		     List<HashMap<String, String>> listinfo = new ArrayList<HashMap<String, String>>();
            listinfo.clear();
		     
		     for(int i = 0; i < oslist.size(); i++){
                 HashMap<String, String> hm = new HashMap<String, String>();
		    	 if(oslist.size() == 0) {
		    		 hm.put("filename", "Empty Folder");
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
                	 hm.put("image", Integer.toString(image[5]));
                 }
                 else {
                	 hm.put("image", Integer.toString(image[1]));
                 }
                 if(displayMode == 3) {
                     int pos = oslist.get(i).get("filename").lastIndexOf("_trash");
                     hm.put("filename", oslist.get(i).get("filename").substring(0, pos));
                 }
                 else {
                     hm.put("filename", oslist.get(i).get("filename"));
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
                 listinfo.add(hm);
		     }
		     
		     listinfo.size();

			ListAdapter adapter = new SimpleAdapter(e, listinfo, R.layout.list_v, new String[] {"image", "filename", "size", "owner"}, new int[] { R.id.icon, R.id.name, R.id.size, R.id.owner}) {
				@Override public View getView(final int position, View convertView, ViewGroup parent) {
				    final View view = super.getView(position, convertView, parent);
				    TextView tvName = (TextView) view.findViewById(R.id.name);
				    tvName.setTypeface(myTypeface);
				    TextView tvType = (TextView) view.findViewById(R.id.size);
				    tvType.setTypeface(myTypeface);

				    if(oslist.get(position).get("type").equals("folder")) {
				    	tvName.setGravity(Gravity.CENTER_VERTICAL | Gravity.TOP);
				    	RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)tvName.getLayoutParams();
				    	layoutParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				    	tvName.setLayoutParams(layoutParams);
				    }

                    ImageView imgview = (ImageView) view.findViewById(R.id.icon);
                    imgview.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                selectedElem = allElem.getJSONObject(position);
                                if(selectedElem2.getJSONObject(position).length() == 0) {
                                    selectedElem2.put(position, selectedElem);
                                    //view.setBackgroundResource(R.drawable.bkg_dark);
                                    view.setBackgroundColor(0xfff0f0f0);
                                }
                                else {
                                    JSONObject obj = new JSONObject();
                                    selectedElem2.put(position, obj);
                                    view.setBackgroundResource(R.drawable.bkg_light);
                                }
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
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
            cancelSelections();
			list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

	            @Override
	            public void onItemClick(AdapterView<?> parent, View view,
	                                    int position, long id) {
	            	String type = oslist.get(+position).get("type");
                    if(displayMode == 3) {
                        return;
                    }
	            	if(type.equals("folder")) {
	            		try {
							currDir = json.getJSONObject(position);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	            		
	            		try {
							hierarchy.add(allElem.getJSONObject(position));
						} catch (JSONException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
	            		new ListContent().execute();
	            	}
	            	else if(type.equals("image")) {
                        try {
                            selectedElem = allElem.getJSONObject(position);
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
			             Intent i = new Intent(e.getApplicationContext(), ImageViewer.class);
                         i.putExtra("file", selectedElem.toString());
			             e.startActivity(i);
	            	}
	            	else if(type.equals("audio")) {
	            		try {
							selectedElem = allElem.getJSONObject(position);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

                        if(mPlayerService != null) {
                            Toast.makeText(e, "Loading audio...", Toast.LENGTH_SHORT).show();
                            audioFilename = oslist.get(position).get("filename");
                            new GetLink().execute();
                        }
	            	}
	            	else {
	            		Toast.makeText(e, "Can not open file", Toast.LENGTH_SHORT).show();
	            	}
	            }
	        });
		} catch (JSONException e) {
			e.printStackTrace();
		}
   		
   	}

    public static class GetLink extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... info) {
            String url = server + "php/files_api.php";
            HashMap<String, String> data = new HashMap<String, String>();

            data.put("target", selectedElem.toString());
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
	
	public static int loginCount = 0;
    public static class Login extends AsyncTask<String, String, String> {
    	 private ProgressDialog pDialog;
    	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(e);
            pDialog.setMessage("Connecting ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
            loginCount++;
    	}
    	
    	@Override
        protected String doInBackground(String... login) {
    		String url = server + "php/core_login.php";
    		HashMap<String, String> data = new HashMap<String, String>();
    		data.put("user", login[0]);
    		data.put("pass", login[1]);

            return Connection.forString(url, data);
    	}
    	 @Override
         protected void onPostExecute(String value) { 
    		pDialog.dismiss();
             if(value == null) {
                 Toast.makeText(e, "No connection to server", Toast.LENGTH_SHORT).show();
             }
             else if(value.equals("1") && loginCount < 2) {
    			 new ListContent().execute();
    			 return;
    		 }
    		 else {
    			 Toast.makeText(e, "Login failed", Toast.LENGTH_SHORT).show();
    		 }
	         Intent i = new Intent(e.getApplicationContext(), org.simpledrive.Login.class);
             e.startActivity(i);
	         e.finish();
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
            for(int i = 0; i < files.length; i++) {
                File file = files[i];
                if(file.isDirectory()) {
                    upload_recursive(orig_path, file);
                }
                else {
                    String rel_dir = file.getParent().substring(orig_path.length()) + "/";
                    Toast.makeText(e, "Uploading " + file.getName() + " to " + rel_dir, Toast.LENGTH_SHORT).show();
                    new Upload().execute(file.getPath(), rel_dir, file.getName());
                }
            }
        }
    }

  public void onBackPressed() {
      if(getSelectedElem().length() != 0) {
          cancelSelections();
          return;
      }
    if (hierarchy.size() > 1) {
        hierarchy.remove(hierarchy.size() - 1);
        currDir = (JSONObject) hierarchy.get(hierarchy.size() - 1);
        new ListContent().execute();
        return;
    }
    else if(displayMode == 3) {
        displayMode = 0;
        new ListContent().execute();
        return;
    }
    super.onBackPressed();
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
            Toast.makeText(e, "Restoring", Toast.LENGTH_SHORT).show();
            new Restore().execute(info.position);
            return true;
  		default:
  			return super.onContextItemSelected(item);
  	}
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
        try {
            data.put("target", getSelectedElem().getJSONObject(0).toString());
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
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
   		data.put("source", getSelectedElem().toString());
        data.put("target", currDir.toString());
   		
			new DownloadFile(new DownloadListener()
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
        return DownloadFile.download(url, data, names[0], path);
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

    private JSONArray getSelectedElem() {
        JSONArray arr = new JSONArray();
        for(int i = 0; i < selectedElem2.length(); i++) {
            try {
                if(selectedElem2.getJSONObject(i).length() != 0) {
                    arr.put(selectedElem2.getJSONObject(i));
                }
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
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
        data.put("source", getSelectedElem().toString());
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

            JSONArray test = new JSONArray();
            test.put(selectedElem);

            data.put("mail", "");
            data.put("key", "");
            data.put("userto", shareUser);
            data.put("pubAcc", Integer.toString(sharePublic));
            data.put("write", Integer.toString(shareWrite));
            data.put("action", "share");
            data.put("target", selectedElem.toString());
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

            JSONArray test = new JSONArray();
            test.put(selectedElem);
            data.put("action", "unshare");
            data.put("target", selectedElem.toString());
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
                Toast.makeText(e, "File unshared", Toast.LENGTH_SHORT).show();
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
            data.put("source", getSelectedElem().toString());
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

            JSONArray test = new JSONArray();
            test.put(selectedElem);
            data.put("test", "test");
            data.put("trash", "true");
            data.put("action", "move");
            data.put("source", test.toString());
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
  		
   		UploadFile myEntity = new UploadFile(new ProgressListener()
				{
					@Override
					public void transferred(Integer num)
					{
						if(num % 5 == 0) {
							publishProgress(num);
						}
					}
				});
        //String dir = currDir.toString();

        return UploadFile.upload(myEntity, url, path[0], path[1], currDir.toString());
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
         if(value != null) {
             Toast.makeText(e, value, Toast.LENGTH_SHORT).show();
         }
   		Toast.makeText(e, "Uploaded", Toast.LENGTH_SHORT).show();
   		 //uploading = false;
   		new ListContent().execute();
   	 }
   }

  protected void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
      e = this;

    setContentView(R.layout.remote_files);
    list = (ListView)findViewById(R.id.list);
    list.setEmptyView(findViewById(R.id.empty_list_item));
    myTypeface = Typeface.createFromAsset(getAssets(), "fonts/robotolight.ttf");
    TextView empty = (TextView) findViewById(R.id.empty_list_item);
	empty.setTypeface(myTypeface);

	
    registerForContextMenu(list);
    
	Intent intent = new Intent();
	intent.setClass(this, AudioService.class);
	getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

    settings = getSharedPreferences("org.simpledrive.shared_pref", 0);
    SharedPreferences.Editor localEditor = settings.edit();
    localEditor.putString("firstrun", "false");
    localEditor.commit();
    bUpload = ((Button)findViewById(R.id.bUpload));
    bUpload.setOnClickListener(new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			bCreate.setVisibility(View.GONE);
			bUpload.setVisibility(View.GONE);
			Intent result = new Intent(RemoteFiles.this, LocalFiles.class);
			startActivityForResult(result, 1);
		}
    });
    bCreate = ((Button)findViewById(R.id.bCreate));
    bCreate.setOnClickListener(new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			new NewFile().execute("folder");
		}
    });
    toggleButton = ((Button)findViewById(R.id.bAdd));
    toggleButton.setOnClickListener(new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if(bCreate.getVisibility() == View.GONE) {
				bCreate.setVisibility(View.VISIBLE);
				bUpload.setVisibility(View.VISIBLE);
			}
			else {
				bCreate.setVisibility(View.GONE);
				bUpload.setVisibility(View.GONE);
			}
		}
    });
    server = settings.getString("server", "");
    firstrun = settings.getString("firstrun", "");
    accMan = AccountManager.get(this);
    sc = accMan.getAccountsByType("org.simpledrive");

    if(!Connection.isLoggedIn() && sc.length == 0) {
      startActivity(new Intent(this, org.simpledrive.Login.class));
      finish();
    }
	else if(sc.length != 0) {
	    username = sc[0].name;
	    hierarchy = new ArrayList();
		new ListContent().execute();
	}

    try {
        currDir.put("filename", "");
        currDir.put("parent", "");
        currDir.put("type", "folder");
        currDir.put("size", 0);
        currDir.put("owner", username);
        currDir.put("hash", 0);
        currDir.put("rootshare", null);
        hierarchy.add(currDir);
	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
    }
  }

  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
    
	try { 
		selectedElem = allElem.getJSONObject(info.position);
        if(selectedElem2.getJSONObject(info.position).length() == 0) {
            cancelSelections();
        }
        selectedElem2.put(info.position, selectedElem);
	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

      boolean trash = (displayMode == 3);

    menu.add(0, 1, 1, "Delete");

    if(!trash) {
        if(getSelectedElem().length() == 1) {
            menu.add(0, 0, 0, "Rename");

            if (!trash && oslist.get(info.position).get("hash").equals("null") && oslist.get(info.position).get("owner").equals(username)) {
                menu.add(0, 2, 2, "Share");
            } else if (!trash && !oslist.get(info.position).get("hash").equals("null")) {
                menu.add(0, 3, 3, "Unshare");
            }
            if (!trash && !oslist.get(info.position).get("type").equals("folder")) {
                menu.add(0, 5, 5, "Download");
            }
        }

        menu.add(0, 4, 4, "Zip");
    }
    else {
        menu.add(0, 6, 6, "Restore");
    }
    menu.setHeaderTitle("Options");
  }

  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main, menu);
    return true;
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
    if (mBound)
      getApplicationContext().unbindService(mServiceConnection);
  }

  public boolean onOptionsItemSelected(MenuItem paramMenuItem)
  {
    switch (paramMenuItem.getItemId()) {
        default:
            return super.onOptionsItemSelected(paramMenuItem);
        case R.id.logout:
            Connection.logout();
            AccountManager am = AccountManager.get(RemoteFiles.this);
            Account aaccount[] = am.getAccounts();
            for (Account anAaccount : aaccount) {
                if (anAaccount.type.equals("org.simpledrive")) {
                    am.removeAccount(new Account(anAaccount.name, anAaccount.type), null, null);
                }
            }
            startActivity(new Intent(getApplicationContext(), org.simpledrive.Login.class));
            finish();
            break;
        case R.id.trash:
            displayMode = 3;
            currDir = (JSONObject) hierarchy.get(0);
            new ListContent().execute();

    }

    return true;
  }

  protected void onPause()
  {
    super.onPause();
  }

  protected void onResume()
  {
    super.onResume();
  }
}