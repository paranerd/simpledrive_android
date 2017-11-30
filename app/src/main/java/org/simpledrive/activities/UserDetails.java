package org.simpledrive.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
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
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;

import java.lang.ref.WeakReference;

public class UserDetails extends AppCompatActivity {
    // General
    public static PrefsFragment prefsFragment;

    public static String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        username = extras.getString("username");

        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();

        setContentView(R.layout.activity_settings);

        initToolbar();
        setToolbarTitle(username);
        new GetStatus(username).execute();
    }

    private void initToolbar() {
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
    }

    public static class PrefsFragment extends PreferenceFragment {
        private UserDetails ctx;

        @Override
        public void onAttach(Activity act) {
            super.onAttach(act);
            ctx = (UserDetails) act;
        }
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
                    new SetAdmin(ctx, username, admin);
                    return false;
                }
            });

            quotaMax = (EditTextPreference) findPreference("user_quota_max");
            quotaMax.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String value = Long.toString(Util.stringToByte(o.toString()));
                    new SetQuota(ctx, username, value).execute();
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

    private static class GetStatus extends AsyncTask<Void, Void, Connection.Response> {
        private String username;

        GetStatus(String username) {
            this.username = username;
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("user", "get");
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

            new GetQuota(username).execute();
        }
    }

    private static class GetQuota extends AsyncTask<Void, Void, Connection.Response> {
        private String username;

        GetQuota(String username) {
            this.username = username;
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("user", "quota");
            multipart.addFormField("user", username);

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
    }

    private static class SetAdmin extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<UserDetails> ref;
        private String username;
        private String enable;
        private ProgressDialog pDialog;

        SetAdmin(UserDetails ctx, String username, String enable) {
            this.ref = new WeakReference<>(ctx);
            this.username = username;
            this.enable = enable;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final UserDetails act = ref.get();
                pDialog = new ProgressDialog(act);
                pDialog.setMessage("Updating...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("user", "setadmin");
            multipart.addFormField("user", username);
            multipart.addFormField("enable", enable);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final UserDetails act = ref.get();
            pDialog.dismiss();
            if (res.successful()) {
                new GetStatus(username).execute();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class SetQuota extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<UserDetails> ref;
        private String username;
        private String value;
        private ProgressDialog pDialog;

        SetQuota(UserDetails ctx, String username, String value) {
            this.ref = new WeakReference<>(ctx);
            this.username = username;
            this.value = value;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (ref.get() != null) {
                final UserDetails act = ref.get();
                pDialog = new ProgressDialog(act);
                pDialog.setMessage("Updating...");
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();
            }
        }

        @Override
        protected Connection.Response doInBackground(Void... pos) {
            Connection multipart = new Connection("user", "setquota");
            multipart.addFormField("user", username);
            multipart.addFormField("value", value);

            return multipart.finish();
        }

        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final UserDetails act = ref.get();
            pDialog.dismiss();
            if (res.successful()) {
                new GetStatus(username).execute();
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}