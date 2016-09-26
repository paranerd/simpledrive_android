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

import java.util.HashMap;

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

        new GetStatus().execute();
    }

    public static class PrefsFragment extends PreferenceFragment {

        private EditTextPreference storageMax;
        private CheckBoxPreference admin;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_userdetails);

            admin = (CheckBoxPreference) findPreference("server_admin");
            admin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String admin = (o.toString().equals("true")) ? "1" : "0";
                    new Update().execute("admin", admin);
                    return false;
                }
            });

            storageMax = (EditTextPreference) findPreference("server_storage_max");
            storageMax.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String value = Long.toString(Util.stringToByte(o.toString()));
                    new Update().execute("storage", value);
                    return false;
                }
            });

            setSummary("server_username", username);
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

    private static class GetStatus extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("users", "get", null);
            multipart.addFormField("user", username);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                try {
                    JSONObject job = new JSONObject(value.get("msg"));
                    String storage_max = job.getString("mem_total");
                    String admin = job.getString("admin");

                    prefsFragment.setSummary("server_storage_max", Util.convertSize(storage_max));
                    prefsFragment.setChecked("server_admin", admin.equals("1"));
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }

            new GetStorageUsed().execute();
        }
    }

    private static class GetStorageUsed extends AsyncTask<Integer, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(Integer... pos) {
            Connection multipart = new Connection("users", "freequota", null);
            multipart.addFormField("user", username);
            multipart.addFormField("value", "0");

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                try {
                    JSONObject job = new JSONObject(value.get("msg"));
                    String storage_used = job.getString("used");

                    prefsFragment.setSummary("server_storage_used", Util.convertSize(storage_used));
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private static class Update extends AsyncTask<String, String, HashMap<String, String>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            e.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected HashMap<String, String> doInBackground(String... pos) {
            Connection multipart = new Connection("users", "update", null);
            multipart.addFormField("user", username);
            multipart.addFormField("key", pos[0]);
            multipart.addFormField("value", pos[1]);

            return multipart.finish();
        }
        @Override
        protected void onPostExecute(HashMap<String, String> value) {
            e.setProgressBarIndeterminateVisibility(false);
            if(value.get("status").equals("ok")) {
                new GetStatus().execute();
            }
            else {
                Toast.makeText(e, value.get("msg"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}