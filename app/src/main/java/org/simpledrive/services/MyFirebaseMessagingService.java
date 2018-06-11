package org.simpledrive.services;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;
import org.simpledrive.R;
import org.simpledrive.activities.UnlockTFA;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static int NOTIFICATION_ID = 4;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            try {
                JSONObject json = new JSONObject(remoteMessage.getData().toString());
                sendPushNotification(json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendPushNotification(JSONObject json) {
        try {
            // Getting the json data
            JSONObject data = json.getJSONObject("data");

            // Parsing json data
            String title = data.getString("title");
            String code = data.getString("code");
            String fingerprint = data.getString("fingerprint");

            // Creating an intent for the notification
            Intent intent = new Intent(getApplicationContext(), UnlockTFA.class);
            intent.putExtra("fingerprint", fingerprint);
            intent.putExtra("code", code);

            // Show Notification
            showNotification(title, code, intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showNotification(String title, String message, Intent intent) {
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        NOTIFICATION_ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);


        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setTicker(title)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(0)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_cloud)
                .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.ic_launcher))
                .setContentText(message)
                .setPriority(Notification.PRIORITY_MAX);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "my_channel_01";// The id of the channel.
            CharSequence name = "simpleDrive";// The user-visible name of the channel.
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);

            notificationManager.createNotificationChannel(mChannel);
            mBuilder = mBuilder.setChannelId(CHANNEL_ID);
        }

        Notification notification = mBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}