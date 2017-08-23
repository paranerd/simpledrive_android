package org.simpledrive.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
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
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.PermissionManager;
import org.simpledrive.helper.SharedPrefManager;
import org.simpledrive.helper.Util;

import java.io.File;
import java.io.IOException;

public class AppSettings extends AppCompatActivity {
    // General
    public static AppSettings ctx;
    private static final String CACHE_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    public static PrefsFragment prefsFragment;
    public static boolean pinEnabled = false;
    public static boolean photosyncEnabled = false;
    private static String photosyncText;
    private static boolean cacheEnabled = false;
    private static final int REQUEST_STORAGE = 6;
    private static String tfaToken;
    private static boolean tfaEnabled = false;

    // Interface
    private static ListPreference fileview;
    private static CheckBoxPreference photosync;
    private static CheckBoxPreference pin;
    private static CheckBoxPreference twoFactor;

    // Request codes
    private static final int REQUEST_SET_PIN = 0;
    private static final int REQUEST_ENABLE_PHOTO_SYNC = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ctx = this;

        initInterface();
        initToolbar();
    }

    private void initInterface() {
        int theme = (SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);
        setContentView(R.layout.activity_settings);
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
        photosyncEnabled = !SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_PHOTO_SYNC, "").equals("");
        photosyncText = SharedPrefManager.getInstance(this).read(SharedPrefManager.TAG_PHOTO_SYNC, "");
        cacheEnabled = new File(CACHE_FOLDER).canRead();
        tfaToken = SharedPrefManager.getInstance(ctx).read(SharedPrefManager.TAG_FIREBASE_TOKEN, "");
        twoFactorEnabled(tfaToken);

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

            String currentView = (SharedPrefManager.getInstance(ctx).read(SharedPrefManager.TAG_LIST_LAYOUT, "list").equals("list")) ? "list": "grid";
            fileview = (ListPreference) findPreference("listlayout");
            fileview.setSummary(currentView.substring(0,1).toUpperCase() + currentView.substring(1));
            fileview.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String view = o.toString();
                    SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_LIST_LAYOUT, view);
                    fileview.setSummary(view.substring(0,1).toUpperCase() + view.substring(1));
                    return true;
                }
            });

            photosync = (CheckBoxPreference) findPreference("photosync");
            photosync.setChecked(photosyncEnabled);
            photosync.setSummary(photosyncText);
            photosync.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (Boolean.parseBoolean(o.toString())) {
                        PermissionManager pm = new PermissionManager(ctx, REQUEST_STORAGE);
                        pm.wantStorage();
                        pm.request("Access files", "Need access to files to create cache folder.", new PermissionManager.TaskListener() {
                            @Override
                            public void onPositive() {
                                Intent i = new Intent(ctx, FileSelector.class);
                                i.putExtra("multi", false);
                                i.putExtra("foldersonly", true);
                                ctx.startActivityForResult(i, REQUEST_ENABLE_PHOTO_SYNC);
                            }

                            @Override
                            public void onNegative() {
                                SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_PHOTO_SYNC, "");
                                photosync.setChecked(false);
                                photosync.setSummary("");
                                Toast.makeText(ctx, "No access to files", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else {
                        SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_PHOTO_SYNC, "");
                        photosync.setSummary("");
                    }
                    return true;
                }
            });

            pin = (CheckBoxPreference) findPreference("pin");
            pin.setChecked(pinEnabled);
            pin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (Boolean.parseBoolean(o.toString())) {
                        Intent i = new Intent(ctx.getApplicationContext(), PinScreen.class);
                        i.putExtra("label", "new PIN");
                        i.putExtra("length", 4);
                        i.putExtra("repeat", true);
                        ctx.startActivityForResult(i, REQUEST_SET_PIN);
                    }
                    else {
                        CustomAuthenticator.setPIN("");
                        pin.setSummary("Disabled");
                    }
                    return true;
                }
            });

            boolean bottomToolbar = SharedPrefManager.getInstance(ctx).read(SharedPrefManager.TAG_BOTTOM_TOOLBAR, false);
            CheckBoxPreference contextMenu = (CheckBoxPreference) findPreference("context");
            contextMenu.setChecked(bottomToolbar);
            contextMenu.setSummary("");
            contextMenu.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_BOTTOM_TOOLBAR, Boolean.parseBoolean(o.toString()));
                    return true;
                }
            });

            Preference clearcache = findPreference("clearcache");
            clearcache.setSummary((cacheEnabled) ? "Size: " + Util.convertSize("" + Util.folderSize(CACHE_FOLDER)) : "No access to cache");
            clearcache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (cacheEnabled) {
                        new android.support.v7.app.AlertDialog.Builder(ctx)
                                .setTitle("Clear Cache")
                                .setMessage("Are you sure you want to clear cache?")
                                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ctx.clearCache();
                                    }

                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                    return false;
                }
            });

            boolean load = SharedPrefManager.getInstance(ctx).read(SharedPrefManager.TAG_LOAD_THUMB, false);
            CheckBoxPreference loadthumb = (CheckBoxPreference) findPreference("loadthumb");
            loadthumb.setChecked(load);
            loadthumb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_LOAD_THUMB, Boolean.parseBoolean(o.toString()));
                    return true;
                }
            });

            final String currentTheme = SharedPrefManager.getInstance(ctx).read(SharedPrefManager.TAG_COLOR_THEME, "light");
            ListPreference theme = (ListPreference) findPreference("colortheme");
            theme.setValue(currentTheme);
            theme.setSummary(currentTheme.substring(0,1).toUpperCase() + currentTheme.substring(1));
            theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_COLOR_THEME, o.toString());
                    ctx.finish();
                    startActivity(ctx.getIntent());
                    return true;
                }
            });

            twoFactor = (CheckBoxPreference) findPreference("twofactor");
            twoFactor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (Boolean.parseBoolean(o.toString())) {
                        ctx.registerTwoFactor(tfaToken);
                    }
                    else {
                        ctx.unregisterTwoFactor(tfaToken);
                    }
                    return true;
                }
            });

            Preference version = findPreference("appversion");
            PackageInfo pInfo;
            try {
                pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
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
                        Intent i = new Intent(ctx, FileSelector.class);
                        i.putExtra("multi", false);
                        i.putExtra("foldersonly", true);
                        ctx.startActivityForResult(i, 1);
                    }
                    else {
                        SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_PHOTO_SYNC, "");
                        photosync.setChecked(false);
                        photosync.setSummary("");
                        Toast.makeText(ctx, "No access to files", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_PHOTO_SYNC:
                if (resultCode == RESULT_OK) {
                    String[] paths = data.getStringArrayExtra("paths");
                    SharedPrefManager.getInstance(ctx).write(SharedPrefManager.TAG_PHOTO_SYNC, paths[0]);
                    photosync.setSummary(paths[0]);
                }
                break;

            case REQUEST_SET_PIN:
                if (resultCode == RESULT_OK) {
                    CustomAuthenticator.setPIN(data.getStringExtra("passphrase"));
                }
                break;
        }
    }

    private void twoFactorEnabled(final String token) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "registered");
                con.addFormField("client", token);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    twoFactor.setChecked(Boolean.parseBoolean(res.getMessage()));
                }
                else {
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void registerTwoFactor(final String token) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "register");
                con.addFormField("client", token);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    tfaEnabled = true;
                    Toast.makeText(ctx, "Registered for Two-Factor-Authentication", Toast.LENGTH_SHORT).show();
                }
                else {
                    twoFactor.setChecked(tfaEnabled);
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void unregisterTwoFactor(final String token) {
        new AsyncTask<Void, Void, Connection.Response>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected Connection.Response doInBackground(Void... params) {
                Connection con = new Connection("twofactor", "unregister");
                con.addFormField("client", token);

                return con.finish();
            }
            @Override
            protected void onPostExecute(Connection.Response res) {
                if (res.successful()) {
                    tfaEnabled = false;
                    Toast.makeText(ctx, "Unregistered from Two-Factor-Authentication", Toast.LENGTH_SHORT).show();
                }
                else {
                    twoFactor.setChecked(tfaEnabled);
                    Toast.makeText(ctx, res.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}