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

public class PinScreen extends AppCompatActivity {
    // General
    private String pin1 = "";
    private String enteredPin = "";
    private String label = "passphrase";
    private boolean repeat = false;
    private int length = 4;

    // Interface
    private TextView pin;
    private TextView error;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        initInterface();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            error.setText(extras.getString("error", ""));
            repeat = extras.getBoolean("repeat", false);
            label = extras.getString("label", "passphrase");
            length = extras.getInt("length", 4);
        }
        clearPin();
    }

    private void initInterface() {
        setContentView(R.layout.activity_pinscreen);

        pin = (TextView) findViewById(R.id.unlock_pin);
        error = (TextView) findViewById(R.id.unlock_error);
        TextView logout = (TextView) findViewById(R.id.unlock_logout);
        logout.setVisibility(View.VISIBLE);
    }

    private void updatePIN() {
        pin.setText(new String(new char[enteredPin.length()]).replace("\0", "\u25CF"));

        if (enteredPin.length() == length) {
            if (pin1.equals("") && repeat) {
                pin1 = enteredPin;
                error.setText("");
                clearPin();
            }
            else if (!pin1.equals("") && repeat && !pin1.equals(enteredPin)) {
                pin1 = "";
                error.setText("Entries don't match");
                clearPin();
            }
            else {
                Intent i = new Intent();
                i.putExtra("passphrase", enteredPin);
                setResult(RESULT_OK, i);
                finish();
            }
        }
    }

    private void clearPin() {
        String text = (pin1.equals("")) ? "Enter " + label : "Repeat " + label;
        enteredPin = "";
        pin.setText("");
        pin.setHint(text);
    }

    private void reset() {
        enteredPin = "";
        pin.setText("");
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