package org.simpledrive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;

import simpledrive.lib.Connection;
import simpledrive.lib.Helper;
import simpledrive.lib.ImageLoader;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class ImageViewer extends Activity {

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_imageviewer);

        Bundle extras = getIntent().getExtras();
		String file = extras.getString("file");
		String filename = extras.getString("filename");
		String parent = extras.getString("parent");

		String tmp_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
		File tmp = new File(tmp_folder);
		if(!tmp.exists()) {
			tmp.mkdir();
		}

		String cachePath = tmp_folder + Helper.md5(parent + filename) + ".jpg";

		File imgFile = new File(cachePath);
		if(imgFile.exists()) {
			Bitmap bmp = BitmapFactory.decodeFile(cachePath);
			displayImage(bmp);
		}
		else {
			loadImage(file.toString(), filename, cachePath);
		}
    }

	public void loadImage(String file, String filename, String path) {
		final ProgressDialog pDialog;
		pDialog = new ProgressDialog(this);
		pDialog.setMessage("Loading image ...");
		pDialog.setIndeterminate(false);
		pDialog.setCancelable(true);
		pDialog.show();

		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		int height = displaymetrics.heightPixels;
		int width = displaymetrics.widthPixels;

		ImageLoader task = new ImageLoader(new ImageLoader.TaskListener() {
			@Override
			public void onFinished(final Bitmap bmp) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						pDialog.dismiss();
						displayImage(bmp);
					}
				});
			}
		});

		task.execute(file, filename, width + "", height + "", path);
	}

	private void displayImage(Bitmap bmp) {
		ImageView imgView = (ImageView) findViewById(R.id.image);
		imgView.setImageBitmap(bmp);
	}
}
