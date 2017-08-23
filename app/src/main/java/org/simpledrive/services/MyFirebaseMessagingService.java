package org.simpledrive.services;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
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


        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        Notification notification;
        notification = mBuilder
                .setTicker(title)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(0)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_cloud)
                .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.ic_launcher))
                .setContentText(message)
                .setPriority(Notification.PRIORITY_MAX)
                .build();

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}