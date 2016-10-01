package org.simpledrive.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.simpledrive.R;

import java.util.HashMap;

import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;

public class Login extends AppCompatActivity {
    // Interface
    private Button btnLogin;
    private EditText txtUsername;
    private EditText txtPassword;
    private EditText txtServername;
    private static String username;
    private static String password;
    private static String server;

    // General
    private static Login e;
    private static ProgressDialog pDialog;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;

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
                    Toast.makeText(getApplicationContext(), "No blank fields", Toast.LENGTH_SHORT).show();
                }
                else {
                    if (server.length() > 3 && !server.substring(0, 4).matches("http")) {
                        server = "http://" + server;
                    }

                    if (!server.substring(server.length() - 1).equals("/")) {
                        server += "/";
                    }
                    new LoginTask().execute();
                }
            }
        });
    }

    public class LoginTask extends AsyncTask<String, String, HashMap<String, String>> {
      	private ProgressDialog pDialog;

      	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(Login.this);
            pDialog.setMessage("Login ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }
      	
      	@Override
        protected HashMap<String, String> doInBackground(String... login) {
            //Connection.setServer(server);
            Connection con = new Connection(server, "core", "login");
            con.addFormField("user", username);
            con.addFormField("pass", password);
            con.forceSetCookie();

            return con.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            pDialog.dismiss();

            if(value == null) {
                Toast.makeText(Login.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
            else if (value.get("status").equals("ok")) {
                if (CustomAuthenticator.addAccount(username, password, server, value.get("msg"))) {
                    Intent i = new Intent(getApplicationContext(), RemoteFiles.class);
                    if (getCallingActivity() != null) {
                        setResult(RESULT_OK, i);
                    }
                    else {
                        startActivity(i);
                    }
                    finish();
                }
                else {
                    Toast.makeText(Login.this, "Error logging in", Toast.LENGTH_SHORT).show();
                }
            }
            else {
                Toast.makeText(Login.this, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }
}