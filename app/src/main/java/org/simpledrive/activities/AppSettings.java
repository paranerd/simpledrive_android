package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
    private static String photosyncStatus;
    private static String currentPhotosyncStatus;

    // Interface
    private static Preference clearcache;
    private static ListPreference fileview;
    private static ListPreference theme;
    private static ListPreference photosync;
    private static CheckBoxPreference loadthumb;
    private static CheckBoxPreference darktheme;
    private static CheckBoxPreference pin;
    private static CheckBoxPreference contextMenu;
    private static Preference version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        e = this;
        settings = getSharedPreferences("org.simpledrive.shared_pref", 0);

        int theme = (settings.getString("colortheme", "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);
        setContentView(R.layout.activity_settings);

        initToolbar();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent();
                    setResult(RESULT_OK, i);
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
        currentPhotosyncStatus = settings.getString("photosync", "disabled");

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();
    }

    public void clearCache() {
        File tmp = new File(tmpFolder);
        try {
            if(tmp.exists()) {
                FileUtils.cleanDirectory(tmp);
            }
        } catch (IOException exp) {
            exp.printStackTrace();
            Toast.makeText(this, "Error clearing cache", Toast.LENGTH_SHORT).show();
        }

        if(tmp.list().length == 0) {
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
            prefsFragment.setSummary("clearcache", "Empty");
        }
    }

    public static class PrefsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_app);

            String currentView = (settings.getString("listlayout", "list").equals("list")) ? "list": "grid";
            fileview = (ListPreference) findPreference("listlayout");
            fileview.setSummary(currentView.substring(0,1).toUpperCase() + currentView.substring(1));
            fileview.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String view = o.toString();
                    settings.edit().putString("listlayout", view).apply();
                    fileview.setSummary(view.substring(0,1).toUpperCase() + view.substring(1));
                    return true;
                }
            });

            photosync = (ListPreference) findPreference("photosync");
            photosync.setValue(currentPhotosyncStatus);
            photosync.setSummary(currentPhotosyncStatus.substring(0,1).toUpperCase() + currentPhotosyncStatus.substring(1));
            photosync.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    photosyncStatus = o.toString();

                    if (photosyncStatus.equals("auto") || photosyncStatus.equals("manual")) {
                        Intent i = new Intent(e, FileSelector.class);
                        i.putExtra("mode", "single");
                        startActivityForResult(i, 1);
                    }
                    else {
                        settings.edit().putString("photosync", photosyncStatus).apply();
                        photosync.setSummary(photosyncStatus.substring(0,1).toUpperCase() + photosyncStatus.substring(1));
                    }
                    return true;
                }
            });

            pin = (CheckBoxPreference) findPreference("pin");
            pin.setChecked(pinEnabled);
            pin.setSummary(pinEnabledText);
            pin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (Boolean.parseBoolean(o.toString())) {
                        startActivity(new Intent(e.getApplicationContext(), EnablePIN.class));
                    }
                    else {
                        CustomAuthenticator.disablePIN();
                        pin.setSummary("Disabled");
                    }
                    return true;
                }
            });

            boolean bottomToolbar = settings.getBoolean("bottomtoolbar", false);
            contextMenu = (CheckBoxPreference) findPreference("context");
            contextMenu.setChecked(bottomToolbar);
            contextMenu.setSummary("");
            contextMenu.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.edit().putBoolean("bottomtoolbar", Boolean.parseBoolean(o.toString())).apply();
                    return true;
                }
            });

            clearcache = findPreference("clearcache");
            clearcache.setSummary("Size: " + Util.convertSize("" + Util.folderSize(tmpFolder)));
            clearcache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new android.support.v7.app.AlertDialog.Builder(e)
                            .setTitle("Clear Cache")
                            .setMessage("Are you sure you want to clear cache?")
                            .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    e.clearCache();
                                }

                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return false;
                }
            });

            boolean load = settings.getBoolean("loadthumb", false);
            loadthumb = (CheckBoxPreference) findPreference("loadthumb");
            loadthumb.setChecked(load);
            loadthumb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.edit().putBoolean("loadthumb", Boolean.parseBoolean(o.toString())).apply();
                    return true;
                }
            });

            final String currentTheme = settings.getString("colortheme", "light");
            theme = (ListPreference) findPreference("colortheme");
            theme.setValue(currentTheme);
            theme.setSummary(currentTheme.substring(0,1).toUpperCase() + currentTheme.substring(1));
            theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.edit().putString("colortheme", o.toString()).apply();
                    e.finish();
                    startActivity(e.getIntent());
                    return true;
                }
            });

            version = findPreference("appversion");
            PackageInfo pInfo;
            try {
                pInfo = e.getPackageManager().getPackageInfo(e.getPackageName(), 0);
                version.setSummary(pInfo.versionName + " (" + pInfo.versionCode + ")");
            } catch (PackageManager.NameNotFoundException e1) {
                e1.printStackTrace();
            }
        }

        public void setSummary(String key, String value) {
            Preference pref = findPreference(key);
            pref.setSummary(value);
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 1) {
                if (resultCode == RESULT_OK) {
                    String[] paths = data.getStringArrayExtra("paths");
                    settings.edit().putString("photosync", photosyncStatus).apply();
                    settings.edit().putString("photosyncFolder", paths[0]).apply();
                    photosync.setSummary(photosyncStatus.substring(0,1).toUpperCase() + photosyncStatus.substring(1));
                }
            }
        }
    }
}