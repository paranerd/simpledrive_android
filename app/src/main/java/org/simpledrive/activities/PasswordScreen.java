package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.simpledrive.R;

public class PasswordScreen extends AppCompatActivity {
    // General
    private boolean repeat = false;
    private int step = 0;
    private String firstPassphrase = "";
    private String label = "passphrase";
    private String title = "Unlock";

    // Interface
    private TextView passphrase;
    private TextView error;
    private TextView tvTitle;
    private Button confirm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_passwordscreen);

        passphrase = (TextView) findViewById(R.id.passphrase);
        error = (TextView) findViewById(R.id.error);
        tvTitle = (TextView) findViewById(R.id.title);
        confirm = (Button) findViewById(R.id.confirm);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            showError(extras.getString("error", ""));
            repeat = extras.getBoolean("repeat", false);
            label = extras.getString("label", "passphrase");
            title = extras.getString("title", "Unlock");
        }

        updateDisplay();
    }

    private void showError(String e) {
        error.setText(e);
    }

    private void updateDisplay() {
        tvTitle.setText(title);
        if (repeat) {
            String t = (step == 0) ? "Set" : "Repeat";
            passphrase.setHint(t + " " + label);
            confirm.setText(t);
        }
        else {
            passphrase.setHint("Enter " + label);
            confirm.setText("Unlock");
        }
        passphrase.setText("");
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.confirm:
                // Repeat required
                if (repeat && step == 0) {
                    firstPassphrase = passphrase.getText().toString();
                    step++;
                    updateDisplay();
                }
                // Passphrases don't match
                else if (repeat && step == 1 &&
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