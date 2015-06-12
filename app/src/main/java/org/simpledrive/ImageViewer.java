package org.simpledrive;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import simpledrive.library.Connection;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageViewer extends Activity {
	
	String file;
	public static final String PREFS_NAME = "org.simpledrive.shared_pref";
	SharedPreferences settings;
	String server;
	public static ImageViewer e;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_imageviewer);
        
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        server = settings.getString("server", "");
		e = this;
        
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
        	try {
				file = URLEncoder.encode(extras.getString("file"), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	new LoadThumb().execute();
    }
	
    public class LoadThumb extends AsyncTask<String, String, Bitmap> {
   	 private ProgressDialog pDialog;
   	@Override
       protected void onPreExecute() {
           super.onPreExecute();
           pDialog = new ProgressDialog(ImageViewer.this);
           pDialog.setMessage("Loading image ...");
           pDialog.setIndeterminate(false);
           pDialog.setCancelable(true);
           pDialog.show();
   	}
   	
   	@Override
       protected Bitmap doInBackground(String... path) {
   		Bitmap bmp = null;
   		try {
   			String url = server + "php/files_api.php?file=" + file + "&action=img";
   			DefaultHttpClient httpClient = Connection.getThreadSafeClient();
   			HttpGet httpGet = new HttpGet(url);
   			
   			HttpResponse response = httpClient.execute(httpGet);
   			HttpEntity resEntity = response.getEntity();
   			
   			if (resEntity != null) {
   				InputStream in = resEntity.getContent();
   				bmp = BitmapFactory.decodeStream(in);
   			}
   		}
   		catch (Exception e)
   		{
   			e.printStackTrace();
   		}

   		return bmp;
   	}
   	 @Override
        protected void onPostExecute(final Bitmap bmp) {
   		pDialog.dismiss();
   			if(bmp != null) {
				runOnUiThread(new Runnable() {
	  			     @Override
	  			     public void run() {
	  	   				ImageView imgView = (ImageView)findViewById(R.id.image);
	  	   				int imgHeight = bmp.getHeight();
	  	   				int imgWidth = bmp.getWidth();
	  	   				
	  	   				DisplayMetrics displaymetrics = new DisplayMetrics();
	  	   				getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
	  	   				int screenHeight = displaymetrics.heightPixels;
	  	   				int screenWidth = displaymetrics.widthPixels;
	  	   				
	  	   				int newWidth, newHeight;
	  	   				float shrinkTo;
	  	   				double ratio = 0.8;
	  	   				if(imgHeight > screenHeight * ratio || imgWidth > screenWidth * ratio) {
	  	   					shrinkTo = Math.min((float)screenHeight / imgHeight, (float)screenWidth / imgWidth);
	  	   					newWidth = (int) (imgWidth * shrinkTo * ratio);
	  	   					newHeight = (int) (imgHeight * shrinkTo * ratio);
	  	   				}
	  	   				else {
	  	   					newWidth = imgWidth;
	  	   					newHeight = imgHeight;
	  	   				}
	  	   				imgView.setImageBitmap(Bitmap.createScaledBitmap(bmp, newWidth, newHeight, false));
	
	  			    }
	  			});
   			}
   			else {
   				Toast.makeText(ImageViewer.this, "Error opening image", Toast.LENGTH_SHORT).show();
   			}
   	 }
   }
}
