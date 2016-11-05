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

public class DownloadManager {
    private static int downloadCurrent = 0;
    private static int downloadTotal = 0;
    private static int downloadSuccessful = 0;
    private static String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    private static boolean downloading = false;
    private static ArrayList<String> downloadQueue = new ArrayList<>();
    private static AppCompatActivity e;
    private static final int NOTIFICATION_ID = 2;
    private static NotificationCompat.Builder mBuilder;
    private static NotificationManager mNotifyManager;

    public static boolean isRunning() {
        return downloading;
    }

    public static void addDownload(AppCompatActivity act, String target) {
        downloadQueue.add(target);
        downloadTotal++;

        if (!downloading) {
            e = act;
            downloading = true;
            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            download();
        }
    }

    private static void download() {
        new AsyncTask<String, Integer, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                Intent intent = new Intent(e, RemoteFiles.class);
                PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

                downloadCurrent++;

                mBuilder = new NotificationCompat.Builder(e)
                        .setContentTitle("Downloading " + downloadCurrent + " of " + downloadTotal)
                        .setContentIntent(pIntent)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_cloud)
                        .setColor(ContextCompat.getColor(e, R.color.darkgreen))
                        .setProgress(100, 0, false);

                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
            }

            @Override
            protected Connection.Response doInBackground(String... params) {
                Connection con = new Connection("files", "get");
                con.setListener(new Connection.ProgressListener() {
                    @Override
                    public void transferred(Integer num) {
                        publishProgress(num);
                    }
                });
                con.addFormField("target", downloadQueue.remove(0));
                con.setDownloadPath(downloadPath, null);
                return con.finish();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                mBuilder.setProgress(100, values[0], false)
                        .setContentTitle("Downloading " + downloadCurrent + " of " + downloadTotal)
                        .setContentText("");
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                downloadSuccessful = (res.successful()) ? downloadSuccessful + 1 : downloadSuccessful;
                if (downloadQueue.size() > 0) {
                    download();
                }
                else {
                    String file = (downloadTotal == 1) ? "file" : "files";
                    mBuilder.setContentTitle("Download complete")
                            .setContentText(downloadSuccessful + " of " + downloadTotal + " " + file + " downloaded")
                            .setOngoing(false)
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                    downloading = false;
                }
            }
        }.execute();
    }
}
