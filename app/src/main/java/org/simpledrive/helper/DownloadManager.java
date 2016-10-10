package org.simpledrive.helper;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import org.simpledrive.R;
import org.simpledrive.activities.RemoteFiles;

import java.util.ArrayList;
import java.util.HashMap;

public class DownloadManager {
    public static int downloadCurrent = 0;
    public static int downloadTotal = 0;
    public static int downloadSuccessful = 0;
    public static String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    public static boolean downloading = false;
    public static ArrayList<String> downloadQueue = new ArrayList<>();
    private static AppCompatActivity e;
    private static final int notificationId = 2;
    private static NotificationCompat.Builder mBuilder;
    private static NotificationManager mNotifyManager;

    public static void addDownload(AppCompatActivity act, String target) {
        downloadQueue.add(target);
        downloadTotal++;

        if (!downloading) {
            e = act;
            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            new Download().execute();
        }
    }

    public static class Download extends AsyncTask<String, Integer, Connection.Response> {
        private String target = downloadQueue.remove(0);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Intent intent = new Intent(e, RemoteFiles.class);
            PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

            downloading = true;
            downloadCurrent++;

            mBuilder = new NotificationCompat.Builder(e)
                    .setContentTitle("Downloading " + downloadCurrent + " of " + downloadTotal)
                    .setContentIntent(pIntent)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_cloud)
                    .setColor(ContextCompat.getColor(e, R.color.darkgreen))
                    .setProgress(100, 0, false);

            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected Connection.Response doInBackground(String... params) {
            Connection con = new Connection("files", "read");
            con.setListener(new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    publishProgress(num);
                }
            });

            con.addFormField("target", target);
            con.setDownloadPath(downloadPath, null);
            return con.finish();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false)
                    .setContentTitle("Downloading " + downloadCurrent + " of " + downloadTotal)
                    .setContentText("");
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            downloadSuccessful = (res.successful()) ? downloadSuccessful + 1 : downloadSuccessful;
            if (downloadQueue.size() > 0) {
                new Download().execute();
            }
            else {
                String file = (downloadTotal == 1) ? "file" : "files";
                mBuilder.setContentTitle("Download complete")
                        .setContentText(downloadSuccessful + " of " + downloadTotal + " " + file + " downloaded")
                        .setOngoing(false)
                        .setProgress(0, 0, false);
                mNotifyManager.notify(notificationId, mBuilder.build());
                downloading = false;
            }
        }
    }
}
