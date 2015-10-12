package simpledrive.lib;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.simpledrive.RemoteFiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;

public class ImageLoader extends AsyncTask<String, String, Bitmap> {
    public final String PREFS_NAME = "org.simpledrive.shared_pref";
    SharedPreferences settings = RemoteFiles.e.getSharedPreferences(PREFS_NAME, 0);
    String server = settings.getString("server", "");

    public interface TaskListener {
        void onFinished(Bitmap bmp);
    }

    // This is the reference to the associated listener
    private final TaskListener taskListener;

    public ImageLoader(TaskListener listener) {
        // The listener reference is passed in through the constructor
        this.taskListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Bitmap doInBackground(String... path) {
        Bitmap bmp = null;
        String file = path[0];
        String filename = path[1];
        String width = path[2];
        String height = path[3];
        String filepath = path[4];

        try {
            String file_enc = URLEncoder.encode(file, "UTF-8");
            String url = server + "php/files_api.php?target=" + file_enc + "&action=img&width=" + width + "&height=" + height;

            DefaultHttpClient httpClient = Connection.getThreadSafeClient();
            HttpGet httpGet = new HttpGet(url);

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity resEntity = response.getEntity();

            if (resEntity != null) {
                InputStream in = resEntity.getContent();
                bmp = BitmapFactory.decodeStream(in);
                File imgFile = new File(filepath);

                if(bmp == null || !imgFile.createNewFile()) {
                    in.close();
                    return null;
                }

                FileOutputStream fos = new FileOutputStream(imgFile);
                bmp = Helper.shrinkBitmap(bmp);

                // Only cache png-s as PNG to save storage
                if(filename.substring(filename.length() - 3).equals("png")) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos);
                }
                else {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                }
                in.close();
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
        if(this.taskListener != null) {
            // And if it is we call the callback function on it.
            this.taskListener.onFinished(bmp);
        }
    }
}
