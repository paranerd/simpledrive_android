package org.simpledrive.helper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.activities.RemoteFiles;
import org.simpledrive.models.FileItem;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Downloader {
    private static int current = 0;
    private static int total = 0;
    private static int successful = 0;
    private static boolean running = false;
    private static ArrayList<QueueItem> queue = new ArrayList<>();
    private static WeakReference<Context> ref;

    private static final int NOTIFICATION_ID = 2;
    private static NotificationCompat.Builder mBuilder;
    private static NotificationManager mNotifyManager;

    /**
     * Set the context
     *
     * @param context Activity context
     */
    public static void setContext(Context context) {
        ref = new WeakReference<>(context);
    }

    /**
     * Determine if a download is running
     *
     * @return If download running
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Add regular file to queue to be cached
     *
     * @param item File
     */
    public static void queueForCache(FileItem item) {
        queue(Util.stringToJsonString(item.getID()), Util.getCacheDir(), item.getID(), 0, 0, false);
    }

    /**
     * Add image file to queue to be cached
     *
     * @param item File
     * @param width Image width
     * @param height Image height
     * @param thumb Whether image is thumbnail
     */
    public static void queueForCache(FileItem item, int width, int height, boolean thumb) {
        String destination = (thumb) ? Util.getThumbDir() : Util.getCacheDir();
        queue(Util.stringToJsonString(item.getID()), destination, item.getID(), width, height, thumb);
    }

    /**
     * Add multiple files to queue to be downloaded
     *
     * @param ids Stringified JSON-Array of FileIDs
     */
    public static void queueForDownload(String ids) {
        queue(ids, Util.getDownloadDir(), null, 0, 0, false);
    }

    /**
     * Add regular file to queue to be downloaded
     *
     * @param item File
     */
    public static void queueForDownload(FileItem item) {
        queue(Util.stringToJsonString(item.getID()), Util.getDownloadDir(), null, 0, 0, false);
    }

    /**
     * Add file to download queue
     *
     * @param id FileID
     * @param destination Destination for downloaded file
     * @param name Filename
     * @param width Image width
     * @param height Image height
     * @param thumb If file is thumbnail
     */
    private static void queue(String id, String destination, String name, int width, int height, boolean thumb) {
        QueueItem next = new QueueItem(id, destination, name, width, height, thumb);
        total++;

        //f (running) {
            queue.add(next);
        //}
        if (!running && ref.get() != null) {
            Context ctx = ref.get();
            mNotifyManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            new DownloadFromQueue().execute();
            Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * TaskListener for finished downloads
     */
    public interface TaskListener {
        void onFinished(boolean success, String path);
    }

    /**
     * Cache regular file directly
     *
     * @param item File to cache
     * @param callback Callback for after finish
     */
    public static void cache(FileItem item, TaskListener callback) {
        new DownloadDirectly(Util.stringToJsonString(item.getID()), Util.getCacheDir(), item.getID(), 0, 0, false, callback).execute();
    }

    /**
     * Cache image directly
     *
     * @param item File to cache
     * @param width Image target width
     * @param height Image target height
     * @param callback Callback for after finish
     */
    public static void cache(FileItem item, int width, int height, boolean thumb, TaskListener callback) {
        String destination = (thumb) ? Util.getThumbDir() : Util.getCacheDir();
        new DownloadDirectly(Util.stringToJsonString(item.getID()), destination, item.getID(), width, height, thumb, callback).execute();
    }

    /**
     * Download regular file directly
     *
     * @param item File to download
     * @param callback Callback for after finish
     */
    public static void download(FileItem item, TaskListener callback) {
        new DownloadDirectly(Util.stringToJsonString(item.getID()), Util.getDownloadDir(), null, 0, 0, false, callback).execute();
    }

    /**
     * Download a single file directly
     */
    private static class DownloadDirectly extends AsyncTask<String, Integer, Connection.Response> {
        String target;
        String destination;
        String name;
        int width;
        int height;
        boolean thumb;
        TaskListener callback;

        DownloadDirectly(String target, String destination, String name, int width, int height, boolean thumb, TaskListener callback) {
            this.target = target;
            this.destination = destination;
            this.name = name;
            this.width = width;
            this.height = height;
            this.thumb = thumb;
            this.callback = callback;
        }

        @Override
        protected Connection.Response doInBackground(String... params) {
            Connection con = new Connection("files", "get");
            con.addFormField("targets", target);
            con.addFormField("width", Integer.toString(width));
            con.addFormField("height", Integer.toString(height));
            con.addFormField("thumbnail", (thumb) ? "1" : "0");
            con.setDownloadPath(destination, name);

            return con.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (callback != null) {
                callback.onFinished(res.successful(), res.getMessage());
            }
        }
    }

    /**
     * Download all files from the queue
     */
    private static class DownloadFromQueue extends AsyncTask<String, Integer, Connection.Response> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            running = true;

            if (ref.get() != null) {
                Context ctx = ref.get();

                Intent intent = new Intent(ctx, RemoteFiles.class);
                intent.setDataAndType(Uri.parse(Util.getDownloadDir()), "resource/folder");
                PendingIntent pIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

                current++;

                mBuilder = new NotificationCompat.Builder(ctx)
                        .setContentTitle("Downloading " + current + " of " + total)
                        .setContentIntent(pIntent)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_cloud)
                        .setColor(ContextCompat.getColor(ctx, R.color.darkgreen))
                        .setProgress(100, 0, false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String CHANNEL_ID = "simpledrive_quiet";// The id of the channel.
                    CharSequence name = "simpleDrive";// The user-visible name of the channel.
                    int importance = NotificationManager.IMPORTANCE_DEFAULT;
                    NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
                    mChannel.setSound(null, null);

                    mNotifyManager.createNotificationChannel(mChannel);
                    mBuilder = mBuilder.setChannelId(CHANNEL_ID);
                }

                mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
            }
        }

        @Override
        protected Connection.Response doInBackground(String... params) {
            QueueItem item = queue.remove(0);

            Connection con = new Connection("files", "get");
            con.setListener(new Connection.ProgressListener() {
                @Override
                public void transferred(Integer num) {
                    publishProgress(num);
                }
            });
            con.addFormField("targets", item.getID());
            con.addFormField("width", Integer.toString(item.getWidth()));
            con.addFormField("height", Integer.toString(item.getHeight()));
            con.addFormField("thumbnail", (item.isThumb()) ? "1" : "0");
            con.setDownloadPath(item.getDestination(), item.getName());
            return con.finish();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mBuilder.setProgress(100, values[0], false)
                    .setContentTitle("Downloading " + current + " of " + total)
                    .setContentText("");
            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            successful = (res.successful()) ? successful + 1 : successful;
            if (queue.size() > 0) {
                new DownloadFromQueue().execute();
            }
            else {
                running = false;
                showFinished();
            }
        }
    }

    /**
     * Show finished notification
     */
    private static void showFinished() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!running && ref.get() != null) {
                    Context ctx = ref.get();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(Util.getDownloadDir()), "resource/folder");
                    PendingIntent pIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

                    String file = (total == 1) ? "file" : "files";
                    mBuilder.setContentTitle("Download complete")
                            .setContentText(successful + " of " + total + " " + file + " downloaded")
                            .setContentIntent(pIntent)
                            .setOngoing(false)
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                }
            }
        }, 1000);
    }

    /**
     * Check if a regular file exists in cache
     *
     * @param item File to check
     * @return String Path to cached file if exists
     */
    public static String isCached(FileItem item) {
        String cachePath = Util.getCacheDir() + item.getID();
        return (new File(cachePath).exists()) ? cachePath : null;
    }

    /**
     * Check if a thumbnail exists in cache that could cover a given size
     *
     * @param item File to check
     * @param size Thumbnail must cover this size
     * @return String Path to cached thumbnail if exists
     */
    public static String isThumbnailCached(FileItem item, int size) {
        String cachePath = Util.getThumbDir() + item.getID();

        if (new File(cachePath).exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(cachePath, o);

            float scale = (1 / Math.min((float) size / o.outHeight, (float) size / o.outWidth));

            return (scale > 0.9) ? cachePath : null;
        }

        return null;
    }

    private static class QueueItem {
        private String id;
        private String destination;
        private String name;
        private int width;
        private int height;
        private boolean thumb;

        private QueueItem(String id, String destination, String name, Integer width, Integer height, boolean thumb) {
            this.id = id;
            this.destination = destination;
            this.name = name;
            this.width = width;
            this.height = height;
            this.thumb = thumb;
        }

        String getID() {
            return this.id;
        }

        String getDestination() {
            return this.destination;
        }

        public String getName() {
            return this.name;
        }

        public int getHeight() {
            return this.height;
        }

        public int getWidth() {
            return this.width;
        }

        public boolean isThumb() {
            return this.thumb;
        }
    }
}
