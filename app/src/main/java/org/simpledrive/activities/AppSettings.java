package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.simpledrive.R;

import java.io.File;
import java.io.IOException;

import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Util;

public class AppSettings extends AppCompatActivity {

    public static AppSettings e;
    private static final String tmpFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    public static PrefsFragment prefsFragment;
    public static SharedPreferences settings;
    public static boolean pinEnabled = false;
    public static String pinEnabledText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;

        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

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
            toolbar.setTitle("Settings");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pinEnabled = CustomAuthenticator.hasPIN();
        pinEnabledText = (pinEnabled) ? "Enabled" : "Disabled";

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();
    }

    public static void clearCache() {
        File tmp = new File(tmpFolder);
        try {
            if(tmp.exists()) {
                FileUtils.cleanDirectory(tmp);
            }
        } catch (IOException exp) {
            exp.printStackTrace();
            Toast.makeText(e, "Error clearing cache", Toast.LENGTH_SHORT).show();
        }

        if(tmp.list().length == 0) {
            Toast.makeText(e, "Cache cleared", Toast.LENGTH_SHORT).show();
            prefsFragment.setSummary("clearcache", "Empty");
        }
    }

    public static class PrefsFragment extends PreferenceFragment {

        private Preference clearcache;
        private ListPreference fileview;
        private CheckBoxPreference loadthumb;
        private CheckBoxPreference pin;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_app);

            String currentView = (settings.getString("view", "").length() == 0) ? "list" : settings.getString("view", "");

            fileview = (ListPreference) findPreference("fileview");
            fileview.setSummary(currentView.substring(0,1).toUpperCase() + currentView.substring(1));
            fileview.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String view = o.toString();
                    settings.edit().putString("view", view).apply();
                    fileview.setSummary(view.substring(0,1).toUpperCase() + view.substring(1));
                    return true;
                }
            });

            pin = (CheckBoxPreference) findPreference("pin");
            pin.setChecked(pinEnabled);
            pin.setSummary(pinEnabledText);
            pin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("true")) {
                        startActivity(new Intent(e.getApplicationContext(), EnablePIN.class));
                    }
                    else {
                        CustomAuthenticator.disablePIN();
                        pin.setSummary("Disabled");
                    }
                    return true;
                }
            });

            clearcache = findPreference("clearcache");
            clearcache.setSummary("Size: " + Util.convertSize("" + Util.folderSize(tmpFolder)));
            clearcache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    clearCache();
                    return false;
                }
            });

            boolean load = (settings.getString("loadthumb", "").length() == 0) ? false : Boolean.valueOf(settings.getString("loadthumb", ""));
            loadthumb = (CheckBoxPreference) findPreference("loadthumb");
            loadthumb.setChecked(load);
            loadthumb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String load = o.toString();
                    settings.edit().putString("loadthumb", load).apply();
                    return true;
                }
            });
        }

        public void setSummary(String key, String value) {
            Preference pref = findPreference(key);
            pref.setSummary(value);
        }
    }
}