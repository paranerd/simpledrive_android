package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.w3c.dom.Text;

public class Unlock extends AppCompatActivity implements View.OnClickListener{
    // Interface
    private TextView pin;
    private TextView error;
    private ImageView clear;
    private Button one;
    private Button two;
    private Button three;
    private Button four;
    private Button five;
    private Button six;
    private Button seven;
    private Button eight;
    private Button nine;
    private Button zero;
    private TextView logout;

    // General
    private String enteredPin = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_unlock);

        pin = (TextView) findViewById(R.id.unlock_pin);
        error = (TextView) findViewById(R.id.unlock_error);
        clear = (ImageView) findViewById(R.id.unlock_clear_pin);

        one = (Button) findViewById(R.id.unlock_one);
        two = (Button) findViewById(R.id.unlock_two);
        three = (Button) findViewById(R.id.unlock_three);
        four = (Button) findViewById(R.id.unlock_four);
        five = (Button) findViewById(R.id.unlock_five);
        six = (Button) findViewById(R.id.unlock_six);
        seven = (Button) findViewById(R.id.unlock_seven);
        eight = (Button) findViewById(R.id.unlock_eight);
        nine = (Button) findViewById(R.id.unlock_nine);
        zero = (Button) findViewById(R.id.unlock_zero);

        logout = (TextView) findViewById(R.id.unlock_logout);

        clear.setOnClickListener(this);

        one.setOnClickListener(this);
        two.setOnClickListener(this);
        three.setOnClickListener(this);
        four.setOnClickListener(this);
        five.setOnClickListener(this);
        six.setOnClickListener(this);
        seven.setOnClickListener(this);
        eight.setOnClickListener(this);
        nine.setOnClickListener(this);
        zero.setOnClickListener(this);

        logout.setVisibility(View.VISIBLE);
        logout.setOnClickListener(this);

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

    private void resetPINText() {
        enteredPin = "";
        pin.setText("");
    }

    private void unlock() {
        if (CustomAuthenticator.unlock(enteredPin)) {
            finish();
            return;
        }

        showError();
        resetPINText();
    }

    @Override
    public void onClick(View view) {
        if (view.getTag() != null) {
            enteredPin += view.getTag().toString();
            updatePIN();
            return;
        }

        switch (view.getId()) {
            case R.id.unlock_clear_pin:
                resetPINText();
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