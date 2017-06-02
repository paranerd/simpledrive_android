package org.simpledrive.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;

public class EnablePIN extends AppCompatActivity implements View.OnClickListener{
    // General
    private int step = 0;
    private String pin1;

    // Interface
    private TextView pin;
    private TextView error;

    private String enteredPin = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_unlock);

        pin = (TextView) findViewById(R.id.unlock_pin);
        reset(true);
        error = (TextView) findViewById(R.id.unlock_error);
    }

    private void setPIN() {
        String text = "";
        for (int i = 0; i < enteredPin.length(); i++) {
            text += "\u25CF";
        }
        pin.setText(text);

        if (enteredPin.length() == 4) {
            if (step == 0) {
                pin1 = enteredPin;
                step++;
                reset(false);
                showError("");
            }
            else {
                if (pin1.equals(enteredPin)) {
                    CustomAuthenticator.setPIN(pin1);
                    finish();
                }
                else {
                    showError("PINs don't match");
                    step = 0;
                    reset(true);
                }
            }
        }
    }

    private void reset(boolean full) {
        enteredPin = "";
        String text = (full || step == 0) ? "Enter new PIN" : "Repeat new PIN";
        pin.setText("");
        pin.setHint(text);
    }

    private void showError(String e) {
        error.setText(e);
    }

    public void onClick(View view) {
        if (view.getTag() != null) {
            enteredPin += view.getTag().toString();
            setPIN();
            return;
        }
        switch (view.getId()) {
            case R.id.unlock_clear_pin:
                reset(false);
                break;
        }

        setPIN();
    }
}