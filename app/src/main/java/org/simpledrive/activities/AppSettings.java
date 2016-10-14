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
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.simpledrive.R;

import java.io.File;
import java.io.IOException;

import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.PermissionManager;
import org.simpledrive.helper.Util;

public class AppSettings extends AppCompatActivity {
    // General
    public static AppSettings e;
    private static final String CACHE_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    public static PrefsFragment prefsFragment;
    public static SharedPreferences settings;
    public static boolean pinEnabled = false;
    public static String pinEnabledText;
    public static boolean photosyncEnabled = false;
    private static String photosyncText;
    private static boolean cacheEnabled = false;
    private static final int REQUEST_STORAGE = 6;

    // Interface
    private static ListPreference fileview;
    private static CheckBoxPreference photosync;
    private static CheckBoxPreference pin;

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
        photosyncEnabled = !settings.getString("photosync", "").equals("");
        photosyncText = settings.getString("photosync", "");
        cacheEnabled = new File(CACHE_FOLDER).canRead();

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();
    }

    public void clearCache() {
        File tmp = new File(CACHE_FOLDER);
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

            photosync = (CheckBoxPreference) findPreference("photosync1");
            photosync.setChecked(photosyncEnabled);
            photosync.setSummary(photosyncText);
            photosync.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (Boolean.parseBoolean(o.toString())) {
                        PermissionManager pm = new PermissionManager(e, REQUEST_STORAGE);
                        pm.wantStorage();
                        pm.request("Access files", "Need access to files to create cache folder.", new PermissionManager.TaskListener() {
                            @Override
                            public void onPositive() {
                                Intent i = new Intent(e, FileSelector.class);
                                i.putExtra("multi", false);
                                i.putExtra("foldersonly", true);
                                e.startActivityForResult(i, 1);
                            }

                            @Override
                            public void onNegative() {
                                settings.edit().putString("photosync", "").apply();
                                photosync.setChecked(false);
                                photosync.setSummary("");
                                Toast.makeText(e, "No access to files", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else {
                        settings.edit().putString("photosync", "").apply();
                        photosync.setSummary("");
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
            CheckBoxPreference contextMenu = (CheckBoxPreference) findPreference("context");
            contextMenu.setChecked(bottomToolbar);
            contextMenu.setSummary("");
            contextMenu.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.edit().putBoolean("bottomtoolbar", Boolean.parseBoolean(o.toString())).apply();
                    return true;
                }
            });

            Preference clearcache = findPreference("clearcache");
            clearcache.setSummary((cacheEnabled) ? "Size: " + Util.convertSize("" + Util.folderSize(CACHE_FOLDER)) : "No access to cache");
            clearcache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (cacheEnabled) {
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
                    }
                    return false;
                }
            });

            boolean load = settings.getBoolean("loadthumb", false);
            CheckBoxPreference loadthumb = (CheckBoxPreference) findPreference("loadthumb");
            loadthumb.setChecked(load);
            loadthumb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.edit().putBoolean("loadthumb", Boolean.parseBoolean(o.toString())).apply();
                    return true;
                }
            });

            final String currentTheme = settings.getString("colortheme", "light");
            ListPreference theme = (ListPreference) findPreference("colortheme");
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

            Preference version = findPreference("appversion");
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Intent i = new Intent(e, FileSelector.class);
                        i.putExtra("multi", false);
                        i.putExtra("foldersonly", true);
                        e.startActivityForResult(i, 1);
                    }
                    else {
                        settings.edit().putString("photosync", "").apply();
                        photosync.setChecked(false);
                        photosync.setSummary("");
                        Toast.makeText(e, "No access to files", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                String[] paths = data.getStringArrayExtra("paths");
                settings.edit().putString("photosync", paths[0]).apply();
                photosync.setSummary(paths[0]);
            }
        }
    }
}