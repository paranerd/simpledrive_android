package org.simpledrive.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.SharedPrefManager;

public class UnlockTFA extends AppCompatActivity {
    private String code = "";
    private String fingerprint = "";
    private TextView txtCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initInterface();
    }

    protected void onResume() {
        super.onResume();

        code = getIntent().getStringExtra("code");
        fingerprint = getIntent().getStringExtra("fingerprint");

        Log.i("debug", "code: " + code);

        txtCode.setText(code);
    }

    private void initInterface() {
        // Set theme
        int theme = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set view
        setContentView(R.layout.activity_unlock_tfa);
        txtCode = (TextView) findViewById(R.id.code);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.yes:
                submitTFA(code, fingerprint);
                break;

            case R.id.no:
                invalidateTFA(fingerprint);
                break;
        }
    }

    private void submitTFA(final String code, final String fingerprint) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Toast.makeText(getApplicationContext(), "Sending code...", Toast.LENGTH_SHORT).show();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "unlock");
                con.addFormField("code", code);
                con.addFormField("fingerprint", fingerprint);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    Toast.makeText(getApplicationContext(), "Unlock successful", Toast.LENGTH_SHORT).show();
                    finish();
                }
                else {
                    Toast.makeText(getApplicationContext(), res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void invalidateTFA(final String fingerprint) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Toast.makeText(getApplicationContext(), "Sending code...", Toast.LENGTH_SHORT).show();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "invalidate");
                con.addFormField("fingerprint", fingerprint);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    Toast.makeText(getApplicationContext(), "Code invalidated", Toast.LENGTH_SHORT).show();
                    finish();
                }
                else {
                    Toast.makeText(getApplicationContext(), res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
