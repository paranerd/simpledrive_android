package org.simpledrive.helper;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
    public static int uploadCurrent = 0;
    public static int uploadTotal = 0;
    public static int uploadSuccessful = 0;
    public static boolean uploading = false;
    public static ArrayList<HashMap<String, String>> uploadQueue = new ArrayList<>();
    private static AppCompatActivity e;

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

    public static void addUpload(AppCompatActivity act, ArrayList<String> paths, String target, Upload.TaskListener listener) {
        for (String path : paths) {
            File file = new File(path);
            if (file.isDirectory()) {
                addRecursive(file.getParent(), file, target);
            }
            else {
                HashMap<String, String> ul_elem = new HashMap<>();
                ul_elem.put("filename", file.getName());
                ul_elem.put("relative", "");
                ul_elem.put("path", path);
                ul_elem.put("target", target);
                uploadQueue.add(ul_elem);
                uploadTotal++;
            }
        }

        if (!uploading) {
            e = act;
            new Upload(listener).execute();
        }
    }

    public static class Upload extends AsyncTask<String, Integer, Connection.Response> {
        private NotificationCompat.Builder mBuilder;
        private NotificationManager mNotifyManager;
        private int notificationId = 1;
        private String filename;

        public interface TaskListener {
            void onFinished();
        }

        private final TaskListener taskListener;

        public Upload(TaskListener listener) {
            this.taskListener = listener;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Intent intent = new Intent(e, RemoteFiles.class);
            PendingIntent pIntent = PendingIntent.getActivity(e, 0, intent, 0);

            uploading = true;
            uploadCurrent++;

            mNotifyManager = (NotificationManager) e.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(e);
            mBuilder.setContentIntent(pIntent)
                    .setContentTitle("Uploading " + uploadCurrent + " of " + uploadTotal)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_cloud)
                    .setColor(ContextCompat.getColor(e, R.color.darkgreen))
                    .setProgress(100, 0, false);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected Connection.Response doInBackground(String... path) {
            HashMap<String, String> ul_elem = uploadQueue.remove(0);
            filename = ul_elem.get("filename");
            String filepath = ul_elem.get("path");
            String relative = ul_elem.get("relative");
            String target = ul_elem.get("target");

            Connection con = new Connection("files", "upload");
            con.setListener(new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    publishProgress(num);
                }
            });

            con.addFormField("paths", relative);
            con.addFormField("target", target);
            con.addFilePart("0", new File(filepath));

            return con.finish();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false)
                    .setContentTitle("Uploading " + uploadCurrent + " of " + uploadTotal)
                    .setContentText(filename);
            mNotifyManager.notify(notificationId, mBuilder.build());
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            uploadSuccessful = (!res.successful()) ? uploadSuccessful : uploadSuccessful + 1;
            if (uploadQueue.size() > 0) {
                new Upload(taskListener).execute();
            }
            else {
                String file = (uploadTotal == 1) ? "file" : "files";
                mBuilder.setContentTitle("Upload complete")
                        .setContentText(uploadSuccessful + " of " + uploadTotal + " " + file + " added")
                        .setOngoing(false)
                        .setProgress(0, 0, false);
                mNotifyManager.notify(notificationId, mBuilder.build());
                uploading = false;

                if (taskListener != null) {
                    taskListener.onFinished();
                }
            }
        }
    }
}
