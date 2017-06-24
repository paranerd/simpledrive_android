package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import org.simpledrive.R;

public class VaultLock extends AppCompatActivity {
    // Interface
    private TextView passphrase;
    private TextView error;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_vaultunlock);

        passphrase = (TextView) findViewById(R.id.passphrase);
        error = (TextView) findViewById(R.id.error);

        boolean error = getIntent().getExtras().getBoolean("error");
        if (error) {
            showError();
        }
    }

    private void showError() {
        error.setText("Passphrase incorrect");
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.unlock:
                Log.i("debug", "unlock with " + passphrase.getText().toString());
                Intent i = new Intent();
                i.putExtra("passphrase", passphrase.getText().toString());
                setResult(RESULT_OK, i);
                finish();
                break;
        }
    }
}