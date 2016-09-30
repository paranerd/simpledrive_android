package org.simpledrive.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;

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

        setError(true);
    }

    private void updatePIN() {
        String text = "";
        for (int i = 0; i < enteredPin.length(); i++) {
            text += "*";
        }
        pin.setText(text);

        if (enteredPin.length() == 4) {
            unlock();
        }
    }

    private void setError(boolean justCheck) {
        String errorMsg = "";
        Long cooldown = CustomAuthenticator.getCooldown();
        Long remainingUnlockAttempts = CustomAuthenticator.getRemainingUnlockAttempts();

        if (cooldown > 0) {
            errorMsg = "Locked for " + (cooldown / 1000) + " second(s).";
        }
        else if (remainingUnlockAttempts < CustomAuthenticator.MAX_UNLOCK_ATTEMPTS) {
            errorMsg = "Incorrect PIN, " + remainingUnlockAttempts + " attempt(s) remaining";
        }
        else if (!justCheck) {
            errorMsg = "Incorrect PIN";
        }
        error.setText(errorMsg);
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

        setError(false);
        resetPINText();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.unlock_one:
                enteredPin += "1";
                break;

            case R.id.unlock_two:
                enteredPin += "2";
                break;

            case R.id.unlock_three:
                enteredPin += "3";
                break;

            case R.id.unlock_four:
                enteredPin += "4";
                break;

            case R.id.unlock_five:
                enteredPin += "5";
                break;

            case R.id.unlock_six:
                enteredPin += "6";
                break;

            case R.id.unlock_seven:
                enteredPin += "7";
                break;

            case R.id.unlock_eight:
                enteredPin += "8";
                break;

            case R.id.unlock_nine:
                enteredPin += "9";
                break;

            case R.id.unlock_zero:
                enteredPin += "0";
                break;

            case R.id.unlock_clear_pin:
                resetPINText();
                break;
        }

        updatePIN();
    }
}