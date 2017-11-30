package org.simpledrive.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Preferences;

import java.lang.ref.WeakReference;

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

        txtCode.setText(code);
    }

    private void initInterface() {
        // Set theme
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set view
        setContentView(R.layout.activity_unlock_tfa);
        txtCode = (TextView) findViewById(R.id.code);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.yes:
                new SubmitTFA(this, code, fingerprint).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                break;

            case R.id.no:
                new InvalidateTFA(this, fingerprint).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                break;
        }
    }

    private static class SubmitTFA extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<UnlockTFA> ref;
        private String code;
        private String fingerprint;

        SubmitTFA(UnlockTFA ctx, String code, String fingerprint) {
            this.ref = new WeakReference<>(ctx);
            this.code = code;
            this.fingerprint = fingerprint;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final UnlockTFA act = ref.get();
                Toast.makeText(act, "Sending code...", Toast.LENGTH_SHORT).show();
            }
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
            if (ref.get() == null) {
                return;
            }

            final UnlockTFA act = ref.get();
            if (res.successful()) {
                Toast.makeText(act, "Unlock successful", Toast.LENGTH_SHORT).show();
                act.finish();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class InvalidateTFA extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<UnlockTFA> ref;
        private String fingerprint;

        InvalidateTFA(UnlockTFA ctx, String fingerprint) {
            this.ref = new WeakReference<>(ctx);
            this.fingerprint = fingerprint;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final UnlockTFA act = ref.get();
                Toast.makeText(act, "Sending code...", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "invalidate");
            con.addFormField("fingerprint", fingerprint);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final UnlockTFA act = ref.get();
            if (res.successful()) {
                Toast.makeText(act, "Code invalidated", Toast.LENGTH_SHORT).show();
                act.finish();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
