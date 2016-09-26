package org.simpledrive.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.simpledrive.R;

import java.util.HashMap;

import org.simpledrive.helper.Connection;

public class Login extends Activity {

    String username;
    String password;
    String server;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_login);

        final EditText txtUsername = (EditText) findViewById(R.id.txtUsername);
        final EditText txtPassword = (EditText) findViewById(R.id.txtPassword);
        final EditText serverName = (EditText) findViewById(R.id.txtServer);

        Button btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                username = txtUsername.getText().toString().replaceAll("\\s+", "");
                password = txtPassword.getText().toString().replaceAll("\\s+", "");
                server = serverName.getText().toString().replaceAll("\\s+", "");

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

            Connection.setServer(server);
        }
      	
      	@Override
        protected HashMap<String, String> doInBackground(String... login) {
            Connection multipart = new Connection("core", "login", null);
            multipart.addFormField("user", username);
            multipart.addFormField("pass", password);
            multipart.forceSetCookie();

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            pDialog.dismiss();

            if(value == null) {
                Toast.makeText(Login.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
            else if (value.get("status").equals("ok")) {
                Account account = new Account(username, "org.simpledrive");
                Bundle userdata = new Bundle();
                userdata.putString("server", server);
                userdata.putString("pin", "1234");
                userdata.putString("token", value.get("msg"));

                Connection.setToken(value.get("msg"));

                AccountManager am = AccountManager.get(Login.this);
                Account aaccount[] = am.getAccounts();

                for (Account anAaccount : aaccount) {
                    if (anAaccount.type.equals("org.simpledrive")) {
                        am.removeAccount(new Account(anAaccount.name, anAaccount.type), null, null, null);
                    }
                }
                if(am.addAccountExplicitly(account, password, userdata)) {
                    Intent i = new Intent(getApplicationContext(), RemoteFiles.class);
                    startActivity(i);
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