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
    private String unlockCode = "";

    // Interface
    private Button btnLogin;
    private EditText txtUsername;
    private EditText txtPassword;
    private EditText txtServername;

    // Request codes
    private final int REQUEST_UNLOCK = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_login);

        CustomAuthenticator.enable(this);

        txtUsername = (EditText) findViewById(R.id.txtUsername);
        txtPassword = (EditText) findViewById(R.id.txtPassword);
        txtServername = (EditText) findViewById(R.id.txtServer);

        btnLogin = (Button) findViewById(R.id.btnLogin);
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
            case REQUEST_UNLOCK:
                if (resultCode == RESULT_OK) {
                    unlockCode = data.getStringExtra("passphrase");
                    login(server, username, password);
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
                Connection con = new Connection(server, "core", "login");
                con.addFormField("user", username);
                con.addFormField("pass", password);
                con.addFormField("code", unlockCode);
                con.forceSetCookie();

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                pDialog.dismiss();

                if (res.successful()) {
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
                        Intent i = new Intent(getApplicationContext(), PasswordScreen.class);
                        String error = (!unlockCode.equals("")) ? "Incorrect code" : "";
                        i.putExtra("error", error);
                        i.putExtra("label", "code");
                        startActivityForResult(i, REQUEST_UNLOCK);
                        //finishActivity(REQUEST_UNLOCK);
                    }
                    else {
                        Toast.makeText(Login.this, res.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }.execute();
    }
}