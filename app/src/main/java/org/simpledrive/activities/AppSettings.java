package org.simpledrive.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Permissions;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;

import java.io.File;
import java.lang.ref.WeakReference;

public class AppSettings extends AppCompatActivity {
    // General
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

        initInterface();
        initToolbar();
    }

    private void initInterface() {
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
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
        photosyncEnabled = !Preferences.getInstance(this).read(Preferences.TAG_PHOTO_SYNC, "").equals("");
        photosyncText = Preferences.getInstance(this).read(Preferences.TAG_PHOTO_SYNC, "");
        cacheEnabled = new File(Util.getCacheDir()).canRead();
        tfaToken = Preferences.getInstance(this).read(Preferences.TAG_FIREBASE_TOKEN, "");
        new TwoFactorEnabled(AppSettings.this, tfaToken).execute();

        prefsFragment = new PrefsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.content, prefsFragment).commit();
    }

    public void clearCache() {
        if (Util.clearCache()) {
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
            prefsFragment.setSummary("clearcache", Util.convertSize("" + Util.folderSize(Util.getCacheDir())));
        }
        else {
            Toast.makeText(this, "Error clearing cache", Toast.LENGTH_SHORT).show();
        }
    }

    public static class PrefsFragment extends PreferenceFragment {
        private AppSettings ctx;

        @Override
        public void onAttach(Activity act) {
            super.onAttach(act);
            ctx = (AppSettings) act;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            initInterface();
        }

        private void initInterface() {
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings_app);

            String currentView = (Preferences.getInstance(getActivity()).read(Preferences.TAG_LIST_LAYOUT, "list").equals("list")) ? "list": "grid";
            fileview = (ListPreference) findPreference("listlayout");
            fileview.setSummary(currentView.substring(0,1).toUpperCase() + currentView.substring(1));
            fileview.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    String view = o.toString();
                    Preferences.getInstance(getActivity()).write(Preferences.TAG_LIST_LAYOUT, view);
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
                        Permissions pm = new Permissions(ctx, REQUEST_STORAGE);
                        pm.wantStorage();
                        pm.request("Access files", "Need access to files to create cache folder.", new Permissions.TaskListener() {
                            @Override
                            public void onPositive() {
                                Intent i = new Intent(ctx, FileSelector.class);
                                i.putExtra("multi", false);
                                i.putExtra("foldersonly", true);
                                ctx.startActivityForResult(i, REQUEST_ENABLE_PHOTO_SYNC);
                            }

                            @Override
                            public void onNegative() {
                                Preferences.getInstance(ctx).write(Preferences.TAG_PHOTO_SYNC, "");
                                photosync.setChecked(false);
                                photosync.setSummary("");
                                Toast.makeText(ctx, "No access to files", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else {
                        Preferences.getInstance(ctx).write(Preferences.TAG_PHOTO_SYNC, "");
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

            boolean bottomToolbar = Preferences.getInstance(ctx).read(Preferences.TAG_BOTTOM_TOOLBAR, false);
            CheckBoxPreference contextMenu = (CheckBoxPreference) findPreference("context");
            contextMenu.setChecked(bottomToolbar);
            contextMenu.setSummary("");
            contextMenu.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    Preferences.getInstance(ctx).write(Preferences.TAG_BOTTOM_TOOLBAR, Boolean.parseBoolean(o.toString()));
                    return true;
                }
            });

            Preference clearcache = findPreference("clearcache");
            clearcache.setSummary((cacheEnabled) ? Util.convertSize("" + Util.folderSize(Util.getCacheDir())) : "No access to cache");
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

            boolean load = Preferences.getInstance(ctx).read(Preferences.TAG_LOAD_THUMB, false);
            CheckBoxPreference loadthumb = (CheckBoxPreference) findPreference("loadthumb");
            loadthumb.setChecked(load);
            loadthumb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    Preferences.getInstance(ctx).write(Preferences.TAG_LOAD_THUMB, Boolean.parseBoolean(o.toString()));
                    return true;
                }
            });

            final String currentTheme = Preferences.getInstance(ctx).read(Preferences.TAG_COLOR_THEME, "light");
            ListPreference theme = (ListPreference) findPreference("colortheme");
            theme.setValue(currentTheme);
            theme.setSummary(currentTheme.substring(0,1).toUpperCase() + currentTheme.substring(1));
            theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    Preferences.getInstance(ctx).write(Preferences.TAG_COLOR_THEME, o.toString());
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
                        new RegisterTwoFactor(ctx, tfaToken).execute();
                    }
                    else {
                        new UnregisterTwoFactor(ctx, tfaToken).execute();
                    }
                    return true;
                }
            });

            Preference version = findPreference("version");
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
                        Intent i = new Intent(this, FileSelector.class);
                        i.putExtra("multi", false);
                        i.putExtra("foldersonly", true);
                        startActivityForResult(i, 1);
                    }
                    else {
                        Preferences.getInstance(this).write(Preferences.TAG_PHOTO_SYNC, "");
                        photosync.setChecked(false);
                        photosync.setSummary("");
                        Toast.makeText(this, "No access to files", Toast.LENGTH_SHORT).show();
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
                    Preferences.getInstance(this).write(Preferences.TAG_PHOTO_SYNC, paths[0]);
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

    private static class TwoFactorEnabled extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<AppSettings> ref;
        private String token;

        TwoFactorEnabled(AppSettings ctx, String token) {
            this.ref = new WeakReference<>(ctx);
            this.token = token;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "registered");
            con.addFormField("client", token);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final AppSettings act = ref.get();
            if (res.successful()) {
                twoFactor.setChecked(Boolean.parseBoolean(res.getMessage()));
            }
            else {
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class RegisterTwoFactor extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<AppSettings> ref;
        private String token;

        RegisterTwoFactor(AppSettings ctx, String token) {
            this.ref = new WeakReference<>(ctx);
            this.token = token;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "register");
            con.addFormField("client", token);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final AppSettings act = ref.get();
            if (res.successful()) {
                tfaEnabled = true;
                Toast.makeText(act, "Registered for Two-Factor-Authentication", Toast.LENGTH_SHORT).show();
            }
            else {
                twoFactor.setChecked(tfaEnabled);
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class UnregisterTwoFactor extends AsyncTask<Void, Void, Connection.Response> {
        private WeakReference<AppSettings> ref;
        private String token;

        UnregisterTwoFactor(AppSettings ctx, String token) {
            this.ref = new WeakReference<>(ctx);
            this.token = token;
        }

        @Override
        protected Connection.Response doInBackground(Void... params) {
            Connection con = new Connection("twofactor", "unregister");
            con.addFormField("client", token);

            return con.finish();
        }
        @Override
        protected void onPostExecute(Connection.Response res) {
            if (ref.get() == null) {
                return;
            }

            final AppSettings act = ref.get();
            if (res.successful()) {
                tfaEnabled = false;
                Toast.makeText(act, "Unregistered from Two-Factor-Authentication", Toast.LENGTH_SHORT).show();
            }
            else {
                twoFactor.setChecked(tfaEnabled);
                Toast.makeText(act, res.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}