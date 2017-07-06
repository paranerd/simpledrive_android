package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.simpledrive.R;

public class PasswordScreen extends AppCompatActivity {
    // General
    private int REQUEST_UNLOCK = 0;
    private int REQUEST_SET_PASSPHRASE = 1;
    private int request_code;
    private int step = 0;
    private String firstPassphrase = "";

    // Interface
    private TextView passphrase;
    private TextView error;

    private Button confirm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_vaultunlock);

        passphrase = (TextView) findViewById(R.id.passphrase);
        error = (TextView) findViewById(R.id.error);
        confirm = (Button) findViewById(R.id.confirm);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            showError(extras.getString("error", ""));
            request_code = extras.getInt("requestCode", REQUEST_UNLOCK);
        }
        updateDisplay();
    }

    private void showError(String e) {
        error.setText(e);
    }

    private void updateDisplay() {
        if (request_code == REQUEST_SET_PASSPHRASE) {
            String t = (step == 0) ? "Set passphrase" : "Repeat passphrase";
            passphrase.setHint(t);
            confirm.setText("Set");
        }
        else {
            passphrase.setHint("Enter passphrase");
            confirm.setText("Unlock");
        }
        passphrase.setText("");
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.confirm:
                // Repeat required
                if (request_code == REQUEST_SET_PASSPHRASE && step == 0) {
                    firstPassphrase = passphrase.getText().toString();
                    step++;
                    updateDisplay();
                }
                // Passphrases don't match
                else if (request_code == REQUEST_SET_PASSPHRASE && step == 1 &&
                        !passphrase.getText().toString().equals(firstPassphrase))
                {
                    showError("Passphrases don't match");
                    firstPassphrase = "";
                    step = 0;
                    updateDisplay();
                }
                // Return passphrase
                else {
                    Intent i = new Intent();
                    i.putExtra("passphrase", passphrase.getText().toString());
                    setResult(RESULT_OK, i);
                    finish();
                }
                break;
        }
    }
}