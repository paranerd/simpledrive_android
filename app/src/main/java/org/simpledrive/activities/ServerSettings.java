package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.simpledrive.R;

import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;

public class ServerSettings extends AppCompatActivity {
    // General
    public static ServerSettings e;
    public static PrefsFragment prefsFragment;
    public static SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();

        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            toolbar.setTitle("Server Settings");
        }

        getStatus();
    }

    public static class PrefsFragment extends PreferenceFragment {

        private Preference showlog;
        private Preference showusers;
        private EditTextPreference uploadLimit;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_server);

            showlog = findPreference("showlog");
            showlog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(e.getApplicationContext(), ServerLog.class));
                    return false;
                }
            });

            showusers = findPreference("users");
            showusers.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(e.getApplicationContext(), Users.class));
                    return false;
                }
            });

            uploadLimit = (EditTextPreference) findPreference("server_upload_max");
            uploadLimit.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String value = Long.toString(Util.stringToByte(o.toString()));
                    e.setUploadLimit(value);
                    return false;
                }
            });

            setSummary("server_address", CustomAuthenticator.getServer());
        }

        public void setSummary(String key, String value) {
            Preference pref = findPreference(key);
            pref.setSummary(value);
        }
    }

    private void getStatus() {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("system", "status");

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    try {
                        JSONObject job = new JSONObject(res.getMessage());
                        String version = job.getString("version");
                        String storage_used = job.getString("storage_used");
                        String storage_total = job.getString("storage_total");
                        String upload_max = job.getString("upload_max");

                        prefsFragment.setSummary("server_version", version);
                        prefsFragment.setSummary("server_storage", Util.convertSize(storage_used) + " / " + Util.convertSize(storage_total));
                        prefsFragment.setSummary("server_upload_max", Util.convertSize(upload_max));
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }.execute();
    }

    private void setUploadLimit(final String value) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("system", "uploadlimit");
                multipart.addFormField("value", value);

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    e.getStatus();
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}