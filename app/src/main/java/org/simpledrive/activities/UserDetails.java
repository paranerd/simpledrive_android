package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
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

import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;

public class UserDetails extends AppCompatActivity {

    public static UserDetails e;
    public static PrefsFragment prefsFragment;
    public static SharedPreferences settings;

    public static String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;

        Bundle extras = getIntent().getExtras();
        username = extras.getString("username");

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();

        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        setToolbarTitle(username);
        getStatus(username);
    }

    public static class PrefsFragment extends PreferenceFragment {

        private EditTextPreference quotaMax;
        private CheckBoxPreference admin;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_userdetails);

            admin = (CheckBoxPreference) findPreference("user_admin");
            admin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String admin = (o.toString().equals("true")) ? "1" : "0";
                    e.setAdmin(username, admin);
                    return false;
                }
            });

            quotaMax = (EditTextPreference) findPreference("user_quota_max");
            quotaMax.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String value = Long.toString(Util.stringToByte(o.toString()));
                    e.setQuota(username, value);
                    return false;
                }
            });

            setSummary("username", username);
        }

        public void setSummary(String key, String value) {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setSummary(value);
            }
        }

        public void setChecked(String key, boolean check) {
            CheckBoxPreference pref = (CheckBoxPreference) findPreference(key);
            if (pref != null) {
                pref.setChecked(check);
            }
        }
    }

    private void getStatus(final String username) {
        new AsyncTask<Void, Void, Connection.Response>() {
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("users", "get");
                multipart.addFormField("user", username);

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    try {
                        JSONObject job = new JSONObject(res.getMessage());
                        String admin = job.getString("admin");

                        prefsFragment.setChecked("user_admin", admin.equals("1"));
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }
                }

                getQuota(username);
            }
        }.execute();
    }

    private void getQuota(final String username) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("users", "quota");
                multipart.addFormField("user", username);
                multipart.addFormField("value", "0");

                return multipart.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    try {
                        JSONObject job = new JSONObject(res.getMessage());
                        String used = job.getString("used");
                        String max = job.getString("max");

                        prefsFragment.setSummary("user_quota_max", Util.convertSize(max));
                        prefsFragment.setSummary("user_quota_used", Util.convertSize(used));
                    } catch (JSONException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }.execute();
    }

    private void setAdmin(final String username, final String enable) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("users", "setadmin");
                multipart.addFormField("user", username);
                multipart.addFormField("enable", enable);

                return multipart.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    e.getStatus(username);
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void setQuota(final String username, final String value) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... pos) {
                Connection multipart = new Connection("users", "setquota");
                multipart.addFormField("user", username);
                multipart.addFormField("value", value);

                return multipart.finish();
            }

            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    e.getStatus(username);
                }
                else {
                    Toast.makeText(e, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}