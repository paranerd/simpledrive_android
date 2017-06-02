package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;

public class Unlock extends AppCompatActivity {
    // Interface
    private TextView pin;
    private TextView error;

    // General
    private String enteredPin = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_unlock);

        pin = (TextView) findViewById(R.id.unlock_pin);
        error = (TextView) findViewById(R.id.unlock_error);

        TextView logout = (TextView) findViewById(R.id.unlock_logout);
        logout.setVisibility(View.VISIBLE);

        showError();
    }

    private void updatePIN() {
        String text = "";
        for (int i = 0; i < enteredPin.length(); i++) {
            text += "\u25CF";
        }
        pin.setText(text);

        if (enteredPin.length() == 4) {
            unlock();
        }
    }

    private void showError() {
        long cooldown = CustomAuthenticator.getCooldown();
        long remainingUnlockAttempts = CustomAuthenticator.getRemainingUnlockAttempts();

        if (cooldown > 0) {
            error.setText("Locked for " + cooldown + " second(s).");
        }
        else if (CustomAuthenticator.MAX_UNLOCK_ATTEMPTS - remainingUnlockAttempts > 0 && remainingUnlockAttempts > 0) {
            error.setText("Incorrect PIN, " + remainingUnlockAttempts + " attempt(s) remaining");
        }
    }

    private void reset() {
        enteredPin = "";
        pin.setText("");
    }

    private void unlock() {
        if (CustomAuthenticator.unlock(enteredPin)) {
            finish();
            return;
        }

        showError();
        reset();
    }

    public void onClick(View view) {
        if (view.getTag() != null) {
            enteredPin += view.getTag().toString();
            updatePIN();
            return;
        }

        switch (view.getId()) {
            case R.id.unlock_clear_pin:
                reset();
                break;

            case R.id.unlock_logout:
                Connection.logout();
                CustomAuthenticator.logout();

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        finish();
                        if (CustomAuthenticator.getAllAccounts(true).size() == 0) {
                            startActivity(new Intent(getApplicationContext(), Login.class));
                        }

                    }
                }, 100);
                break;
        }
    }
}