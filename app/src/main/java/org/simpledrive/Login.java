package org.simpledrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.HashMap;

import simpledrive.library.Connection;
 
public class Login extends Activity {
     
    // Email, password edittext
    EditText txtUsername, txtPassword, serverName;
     
    // login button
    Button btnLogin;
    
    static String server;
    static String username;
    static String password;
 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_login);              
         
        // Email, Password input text
        txtUsername = (EditText) findViewById(R.id.txtUsername);
        txtPassword = (EditText) findViewById(R.id.txtPassword);
        serverName = (EditText) findViewById(R.id.txtServer);
         
        // Login button
        btnLogin = (Button) findViewById(R.id.btnLogin);
         
        // Login button click event
        btnLogin.setOnClickListener(new View.OnClickListener() {
             
            @Override
            public void onClick(View arg0) {
                // Get server, username, password from EditText
                username = txtUsername.getText().toString();
                password = txtPassword.getText().toString();
                server = serverName.getText().toString();
          		
                // Check if server, username, password is filled
                if(server.trim().length() == 0 || username.trim().length() == 0 || password.trim().length() == 0) {
                	Toast.makeText(getApplicationContext(), "No blank fields", Toast.LENGTH_SHORT).show();
                    return;
                }      
                else {
                    if (server.trim().length() > 3 && !server.substring(0, 4).matches("http")) {
                        server = (new StringBuilder("http://")).append(server).toString();
                    }

                    if(!server.substring(server.length() - 1).equals("/")) {
                        server += "/";
                    }
                	new LoginTask().execute(username, password);
                }
            }
        });
    }
    
    public class LoginTask extends AsyncTask<String, String, String> {
      	private ProgressDialog pDialog;
      	@Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(org.simpledrive.Login.this);
            pDialog.setMessage("Login ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }
      	
      	@Override
        protected String doInBackground(String... login) {
            String user = login[0];
            String pass = login[1];
            String url = server + "php/core_login.php";
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("user", user);
            data.put("pass", pass);

            return Connection.forString(url, data);
        }
        @Override
        protected void onPostExecute(String value) {
            pDialog.dismiss();
            if (value != null) {
                if(value.equals("1")) {
                    SharedPreferences.Editor editor = getSharedPreferences("org.simpledrive.shared_pref", 0).edit();
                    editor.putString("server", server).commit();
                    Account account = new Account(txtUsername.getText().toString(), "org.simpledrive");
                    Bundle userdata = new Bundle();
                    userdata.putString("SERVER", server);
                    AccountManager am = AccountManager.get(org.simpledrive.Login.this);
                    Account aaccount[] = am.getAccounts();

                    for (Account anAaccount : aaccount) {
                        if (anAaccount.type.equals("org.simpledrive")) {
                            am.removeAccount(new Account(anAaccount.name, anAaccount.type), null, null);
                        }
                    }

                    am.addAccountExplicitly(account, txtPassword.getText().toString(), userdata);
                    Intent i = new Intent(getApplicationContext(), RemoteFiles.class);
                    startActivity(i);
                    finish();
                }
                else {
                   Toast.makeText(org.simpledrive.Login.this, "Login failed " + value, Toast.LENGTH_SHORT).show();
                }
            }
            else {
                Toast.makeText(org.simpledrive.Login.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        }
    }
}