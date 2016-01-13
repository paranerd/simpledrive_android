package simpledrive.lib;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.simpledrive.R;
import org.simpledrive.RemoteFiles;
import org.simpledrive.ShareFiles;

import java.util.HashMap;

public class Uploader extends AsyncTask<HashMap<String, String>, Integer, HashMap<String, String>> {
    private final TaskListener taskListener;
    private int uploadCurrent;
    private int fullCurrent;
    private int fullTotal;
    private String token;
    private String server;

    public interface TaskListener {
        void onFinished(Boolean value);
    }

    public Uploader(TaskListener listener, int uploadCurrent, int fullCurrent, int fullTotal, String token, String server) {
        this.taskListener = listener;
        this.uploadCurrent = uploadCurrent;
        this.fullCurrent = fullCurrent;
        this.fullTotal = fullTotal;
        this.token = token;
        this.server = server;
    }

    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotifyManager;
    private int notificationId = 1;
    private String filename;

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        Log.i("starting upload", Integer.toString(uploadCurrent));

        Intent intent = new Intent(RemoteFiles.e, RemoteFiles.class);
        PendingIntent pIntent = PendingIntent.getActivity(RemoteFiles.e, 0, intent, 0);

        mNotifyManager = (NotificationManager) RemoteFiles.e.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(RemoteFiles.e);
        mBuilder.setContentIntent(pIntent)
                .setContentTitle("Uploading " + fullCurrent + " of " + fullTotal)
                .setOngoing(true)
                .setSmallIcon(R.drawable.cloud_icon_notif)
                .setProgress(100, 0, false);
        mNotifyManager.notify(notificationId, mBuilder.build());
    }

    @Override
    protected HashMap<String, String> doInBackground(HashMap<String, String>... ul_elem) {
        filename = ul_elem[0].get("filename");
        String filepath = ul_elem[0].get("path");
        String relative = ul_elem[0].get("relative");
        String target = ul_elem[0].get("target");

        String url = server + "api/files.php";

        simpledrive.lib.Upload myEntity = new simpledrive.lib.Upload(new Upload.ProgressListener() {
            @Override
            public void transferred(Integer num) {
                if(num % 5 == 0) {
                    publishProgress(num);
                }
            }
        });

        return simpledrive.lib.Upload.upload(myEntity, url, filepath, relative, target, token);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        //Log.i("progress update", Integer.toString(values[0]));
        mBuilder.setProgress(100, values[0], false)
                .setContentTitle("Uploading " + fullCurrent + " of " + fullTotal)
                .setContentText(filename);
        mNotifyManager.notify(notificationId, mBuilder.build());
    }

    @Override
    protected void onPostExecute(HashMap<String, String> value) {
        Log.i("post execute", Integer.toString(uploadCurrent));
        if(this.taskListener != null) {
            this.taskListener.onFinished(true);
        }
        /*uploadSuccessful = (value == null || !value.get("status").equals("ok")) ? uploadSuccessful : uploadSuccessful + 1;
        fullSuccessful = ShareFiles.uploadSuccessful + uploadSuccessful;
        if(uploadQueue.size() > 0) {
                try {
                    Log.i("waiting", "1ms");
                    Thread.sleep(1);
                    Log.i("after", "sleep");
                    new Upload().execute();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
        }
        else {
            if(!ShareFiles.uploading) {
                String file = (fullTotal == 1) ? "file" : "files";
                mBuilder.setContentTitle("Upload complete")
                        .setContentText(fullSuccessful + " of " + fullTotal + " " + file + " added")
                        .setOngoing(false)
                        .setProgress(0, 0, false);
                mNotifyManager.notify(notificationId, mBuilder.build());
                uploading = false;
            }
            new ListContent().execute();
        }*/
    }
}