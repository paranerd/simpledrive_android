package simpledrive.lib;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;

public class ImageLoader extends AsyncTask<String, String, Bitmap> {
    private final TaskListener taskListener;

    public interface TaskListener {
        void onFinished(Bitmap bmp);
    }

    public ImageLoader(TaskListener listener) {
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
        String token = path[5];
        String server = path[6];

        try {
            String file_enc = URLEncoder.encode(file, "UTF-8");
            String url = server + "api/files.php?token=" + token + "&target=" + file_enc + "&action=img&width=" + width + "&height=" + height;

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

                // Only cache png-s as PNG to save storage
                if(filename.substring(filename.length() - 3).equals("png")) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos);
                } else {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                }
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bmp;
    }

    @Override
    protected void onPostExecute(final Bitmap bmp) {
        if(this.taskListener != null) {
            this.taskListener.onFinished(bmp);
        }
    }
}
