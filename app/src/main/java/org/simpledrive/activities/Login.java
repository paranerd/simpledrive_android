package org.simpledrive.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;

import java.lang.ref.WeakReference;

public class Login extends AppCompatActivity {
    // General
    private String username;
    private String password;
    private String server;
    private static boolean requestedTFAUnlock = false;

    // Interface
    private EditText txtUsername;
    private EditText txtPassword;
    private EditText txtServername;

    // Request codes
    private static final int REQUEST_TFA_CODE = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Need to set context again because RemoteFiles/ShareFiles finish
        CustomAuthenticator.setContext(this);
        Connection.init(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_login);

        txtUsername = (EditText) findViewById(R.id.txtUsername);
        txtPassword = (EditText) findViewById(R.id.txtPassword);
        txtServername = (EditText) findViewById(R.id.txtServer);

        Button btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                username = txtUsername.getText().toString().replaceAll("\\s+", "");
                password = txtPassword.getText().toString().replaceAll("\\s+", "");
                server = txtServername.getText().toString().replaceAll("\\s+", "");

                // Check if server, username, password is filled
                if (server.length() == 0 || username.length() == 0 || password.length() == 0) {
                    Toast.makeText(getApplicationContext(), R.string.no_blank_fields, Toast.LENGTH_SHORT).show();
                }
                else {
                    // Clean-up server-string
                    if (server.length() > 3 && !server.substring(0, 4).matches("http")) {
                        server = "http://" + server;
                    }

                    if (!server.substring(server.length() - 1).equals("/")) {
                        server += "/";
                    }

                    new LoginTask(Login.this, server, username, password).execute();
                }
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_TFA_CODE:
                requestedTFAUnlock = false;
                if (resultCode == RESULT_OK) {
                    new SubmitTFA(Login.this, data.getStringExtra("passphrase")).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                else if (CustomAuthenticator.getToken().equals("")) {
                    Toast.makeText(Login.this, "Two-Factor-Authentication failed", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private static class LoginTask extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Login> ref;
        private ProgressDialog pDialog;
        private String server;
        private String username;
        private String password;

        LoginTask(Login ctx, String server, String username, String password) {
            this.ref = new WeakReference<>(ctx);
            this.server = server;
            this.username = username;
            this.password = password;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final Login act = ref.get();
                pDialog = new ProgressDialog(act);
                pDialog.setMessage("Login...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... login) {
            Connection con = (requestedTFAUnlock) ? new Connection(server, "core", "login", 30000) : new Connection(server, "core", "login");
            con.addFormField("user", username);
            con.addFormField("pass", password);
            con.addFormField("callback", String.valueOf(requestedTFAUnlock));

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Login act = ref.get();
            pDialog.dismiss();

            if (requestedTFAUnlock) {
                act.finishActivity(REQUEST_TFA_CODE);
            }
            requestedTFAUnlock = false;

            if (res.successful()) {
                if (CustomAuthenticator.accountExists(username, server)) {
                    Toast.makeText(act, R.string.account_exists, Toast.LENGTH_SHORT).show();
                }
                else if (CustomAuthenticator.addAccount(username, password, server, res.getMessage())) {
                    Intent i = new Intent(act, RemoteFiles.class);
                    if (act.getCallingActivity() != null) {
                        act.setResult(RESULT_OK, i);
                    }
                    else {
                        act.startActivity(i);
                    }
                    act.finish();
                }
                else {
                    // An error occurred
                    Toast.makeText(act, R.string.login_error, Toast.LENGTH_SHORT).show();
                }
            }
            else {
                if (res.getStatus() == 403) {
                    // TFA-Code required
                    act.requestTFA(res.getMessage());
                    new LoginTask(act, server, username, password).execute();
                }
                else {
                    // An error occurred
                    Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static class SubmitTFA extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<Login> ref;
        private String code;

        SubmitTFA(Login ctx, String code) {
            this.ref = new WeakReference<>(ctx);
            this.code = code;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (ref.get() == null) {
                final Login act = ref.get();
                Toast.makeText(act, "Evaluating Code...", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "unlock");
            con.addFormField("code", code);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final Login act = ref.get();
            if (!res.successful()) {
                act.requestTFA(res.getMessage());
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestTFA(String error) {
        Intent i = new Intent(getApplicationContext(), PinScreen.class);
        i.putExtra("error", error);
        i.putExtra("label", "2FA-code");
        i.putExtra("length", 5);
        startActivityForResult(i, REQUEST_TFA_CODE);
        requestedTFAUnlock = true;
    }
}