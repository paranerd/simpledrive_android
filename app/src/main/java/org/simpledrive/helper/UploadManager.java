package org.simpledrive.helper;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import org.simpledrive.R;
import org.simpledrive.activities.RemoteFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class UploadManager {
    private static int uploadCurrent = 0;
    private static int uploadTotal = 0;
    private static int uploadSuccessful = 0;
    private static int NOTIFICATION_ID = 1;
    private static ArrayList<HashMap<String, String>> uploadQueue = new ArrayList<>();
    private static AppCompatActivity e;
    private static SharedPreferences settings;

    public static boolean isRunning() {
        return uploadQueue.size() > 0;
    }

    private static void addRecursive(String orig_path, File dir, String target) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                addRecursive(orig_path, file, target);
            }
            else {
                String rel_dir = file.getParent().substring(orig_path.length()) + "/";
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", rel_dir);
                ul_elem.put("path", file.getPath());
                ul_elem.put("target", target);
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }
    }

    public static void addUpload(AppCompatActivity act, ArrayList<String> paths, String target, String photosync, TaskListener listener) {
        for (String path : paths) {
            File file = new File(path);
            if (!file.canRead()) {
                continue;
            }
            else if (file.isDirectory()) {
                addRecursive(file.getParent(), file, target);
            }
            else {
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", "");
                ul_elem.put("path", path);
                ul_elem.put("target", target);
                ul_elem.put("photosync", photosync);
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }

        if (uploadQueue.size() > 0 && uploadQueue.size() == paths.size()) {
            e = act;
            settings = e.getSharedPreferences("org.simpledrive.shared_pref", 0);
            upload(listener);
        }
    }

    public interface TaskListener {
        void onFinished();
    }

    private static void upload(final TaskListener taskListener) {
        new AsyncTask<String, Integer, Connection.Response>() {
            private NotificationCompat.Builder mBuilder;
            private NotificationManager mNotifyManager;
            private String filename;
            private HashMap<String, String> ul_elem;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                Intent intent = new Intent(e, RemoteFiles.class);
                PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

                uploadCurrent++;

                mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
                mBuilder = new NotificationCompat.Builder(e);
                mBuilder.setContentIntent(pIntent)
                        .setContentTitle("Uploading " + uploadCurrent + " of " + uploadTotal)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_cloud)
                        .setColor(ContextCompat.getColor(e, R.color.darkgreen))
                        .setProgress(100, 0, false);
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
            }

            @Override
            protected Connection.Response doInBackground(String... path) {
                ul_elem = uploadQueue.remove(0);
                filename = ul_elem.get("filename");

                Connection con = new Connection("files", "upload");
                con.setListener(new Connection.ProgressListener() {
                    @Override
                    public void transferred(Integer num) {
                        publishProgress(num);
                    }
                });

                con.addFormField("target", ul_elem.get("target"));
                con.addFormField("paths", ul_elem.get("relative"));
                con.addFilePart("0", new File(ul_elem.get("path")));

                return con.finish();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                mBuilder.setProgress(100, values[0], false)
                        .setContentTitle("Uploading " + uploadCurrent + " of " + uploadTotal)
                        .setContentText(filename);
                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                uploadSuccessful = (!res.successful()) ? uploadSuccessful : uploadSuccessful + 1;
                if (ul_elem.get("photosync").equals("1")) {
                    settings.edit().putLong("lastPhotoSync", Util.getTimestamp()).apply();
                }

                if (uploadQueue.size() > 0) {
                    upload(taskListener);
                }
                else {
                    String file = (uploadTotal == 1) ? "file" : "files";
                    mBuilder.setContentTitle("Upload complete")
                            .setContentText(uploadSuccessful + " of " + uploadTotal + " " + file + " added")
                            .setOngoing(false)
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());

                    if (taskListener != null) {
                        taskListener.onFinished();
                    }
                }
            }
        }.execute();
    }
}
