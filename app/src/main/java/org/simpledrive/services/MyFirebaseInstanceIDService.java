package org.simpledrive.services;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.simpledrive.helper.SharedPrefManager;

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        String token = SharedPrefManager.getInstance(getApplicationContext()).read(SharedPrefManager.TAG_FIREBASE_TOKEN, "");
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        storeToken(refreshedToken);

        if (!token.equals("")) {
            SharedPrefManager.getInstance(getApplicationContext()).write(SharedPrefManager.TAG_FIREBASE_TOKEN_OLD, token);
        }
    }

    private void storeToken(String token) {
        SharedPrefManager.getInstance(getApplicationContext()).write(SharedPrefManager.TAG_FIREBASE_TOKEN, token);
    }
}