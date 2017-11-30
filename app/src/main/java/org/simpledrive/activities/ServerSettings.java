package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
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
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;

import java.lang.ref.WeakReference;

public class ServerSettings extends AppCompatActivity {
    // General
    public static PrefsFragment prefsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
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

        new GetStatus().execute();
    }

    public static class PrefsFragment extends PreferenceFragment {
        private ServerSettings act;
        private Preference showlog;
        private Preference showusers;
        private EditTextPreference uploadLimit;

        @Override
        public void onAttach(Context ctx) {
            super.onAttach(ctx);
            act = (ServerSettings) ctx;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_server);

            showlog = findPreference("showlog");
            showlog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(act, ServerLog.class));
                    return false;
                }
            });

            showusers = findPreference("users");
            showusers.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(act, Users.class));
                    return false;
                }
            });

            uploadLimit = (EditTextPreference) findPreference("server_upload_max");
            uploadLimit.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String value = Long.toString(Util.stringToByte(o.toString()));
                    new SetUploadLimit(act, value).execute();
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

    private static class GetStatus extends AsyncTask<Void, Void, Connection.Response> {
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
    }

    private static class SetUploadLimit extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<ServerSettings> ref;
        private String value;

        SetUploadLimit(ServerSettings ctx, String value) {
            this.ref = new WeakReference<>(ctx);
            this.value = value;
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("system", "uploadlimit");
            multipart.addFormField("value", value);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final ServerSettings act = ref.get();
            if (res.successful()) {
                new GetStatus().execute();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}