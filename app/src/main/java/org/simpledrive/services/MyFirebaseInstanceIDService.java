package org.simpledrive.services;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.simpledrive.helper.Preferences;

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        String token = Preferences.getInstance(getApplicationContext()).read(Preferences.TAG_FIREBASE_TOKEN, "");
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        storeToken(refreshedToken);

        if (!token.equals("")) {
            Preferences.getInstance(getApplicationContext()).write(Preferences.TAG_FIREBASE_TOKEN_OLD, token);
        }
    }

    private void storeToken(String token) {
        Preferences.getInstance(getApplicationContext()).write(Preferences.TAG_FIREBASE_TOKEN, token);
    }
}