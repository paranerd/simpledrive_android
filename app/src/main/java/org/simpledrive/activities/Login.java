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

public class Login extends AppCompatActivity {
    // General
    private String username;
    private String password;
    private String server;
    private boolean waitForTFAUnlock = false;

    // Interface
    private EditText txtUsername;
    private EditText txtPassword;
    private EditText txtServername;

    // Request codes
    private final int REQUEST_TFA_CODE = 0;

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

                    login(server, username, password);
                }
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_TFA_CODE:
                waitForTFAUnlock = false;
                if (resultCode == RESULT_OK) {
                    submitTFA(data.getStringExtra("passphrase"));
                }
                else if (CustomAuthenticator.getToken().equals("")) {
                    Toast.makeText(Login.this, "Two-Factor-Authentication failed", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void login(final String server, final String username, final String password) {
        final ProgressDialog pDialog = new ProgressDialog(Login.this);
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                pDialog.setMessage("Login...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();
            }

            @Override
            protected Connection.Response doInBackground(Void... login) {
                Connection con = (waitForTFAUnlock) ? new Connection(server, "core", "login", 30000) : new Connection(server, "core", "login");
                con.addFormField("user", username);
                con.addFormField("pass", password);
                con.addFormField("callback", String.valueOf(waitForTFAUnlock));

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                pDialog.dismiss();

                if (res.successful()) {
                    if (waitForTFAUnlock) {
                        finishActivity(REQUEST_TFA_CODE);
                    }
                    waitForTFAUnlock = false;

                    if (CustomAuthenticator.addAccount(username, password, server, res.getMessage())) {
                        Intent i = new Intent(getApplicationContext(), RemoteFiles.class);
                        if (getCallingActivity() != null) {
                            setResult(RESULT_OK, i);
                        } else {
                            startActivity(i);
                        }
                        finish();
                    } else if (CustomAuthenticator.accountExists(username, server)) {
                        Toast.makeText(Login.this, R.string.account_exists, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Login.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    if (res.getStatus() == 403) {
                        // TFA-Code required
                        requestTFA(res.getMessage());
                        login(server, username, password);
                    }
                    else {
                        if (waitForTFAUnlock) {
                            finishActivity(REQUEST_TFA_CODE);
                        }
                        waitForTFAUnlock = false;

                        Toast.makeText(Login.this, res.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }.execute();
    }

    private void submitTFA(final String code) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Toast.makeText(getApplicationContext(), "Evaluating Code...", Toast.LENGTH_SHORT).show();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "unlock");
                con.addFormField("code", code);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (!res.successful()) {
                    requestTFA(res.getMessage());
                    Toast.makeText(getApplicationContext(), res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void requestTFA(String error) {
        Intent i = new Intent(getApplicationContext(), PinScreen.class);
        i.putExtra("error", error);
        i.putExtra("label", "2FA-code");
        i.putExtra("length", 5);
        startActivityForResult(i, REQUEST_TFA_CODE);
        waitForTFAUnlock = true;
    }
}