package org.simpledrive.activities;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItem;

public class VaultEntry extends AppCompatActivity implements TextWatcher {
    // Constants
    private final int REQUEST_LOGO = 0;
    private final int REQUEST_PASSWORD = 1;
    private final int USER_NOTIFICATION_ID = 1;
    private final int PASS_NOTIFICATION_ID = 2;

    // General
    private boolean saved = true;
    private VaultItem item;
    private String defaultGroup = "General";

    // Notification
    private NotificationManager mNotifyManager;
    private VaultBCReceiver receiver;
    public static final String COPY_USER = "org.simpledrive.action.copyuser";
    public static final String COPY_PASS = "org.simpledrive.action.copypass";

    // Interface
    private EditText title;
    private EditText group;
    private EditText url;
    private EditText username;
    private EditText password;
    private EditText note;
    private ImageView logo;
    private Button passwordCopy;
    private Button passwordGenerate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);

        initInterface();
        initToolbar();

        // Init receiver
        initReceiver();

        // Init entry
        item = getIntent().getParcelableExtra("item");
        if (item == null) {
            item = new VaultItem();
        }

        display();
        saved = true;

        // Show notifications
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showPasswordNotification();
        showUsernameNotification();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initInterface() {
        // Set theme
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Set view
        setContentView(R.layout.activity_vault_entry);

        title = (EditText) findViewById(R.id.vault_title);
        title.addTextChangedListener(this);

        group = (EditText) findViewById(R.id.vault_group);
        group.addTextChangedListener(this);

        url = (EditText) findViewById(R.id.vault_url);
        url.addTextChangedListener(this);

        username = (EditText) findViewById(R.id.vault_user);
        username.addTextChangedListener(this);

        password = (EditText) findViewById(R.id.vault_pass);
        password.addTextChangedListener(this);

        note = (EditText) findViewById(R.id.vault_note);
        note.addTextChangedListener(this);
        note.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                // Disallow the touch request for parent scroll on touch of child view
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
        });

        passwordCopy = (Button) findViewById(R.id.copypass);
        passwordGenerate = (Button) findViewById(R.id.generatepass);

        FrameLayout logo_wrapper = (FrameLayout) findViewById(R.id.logo_wrapper);
        logo_wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), LogoSelector.class);
                startActivityForResult(i, REQUEST_LOGO);
            }
        });

        logo = (ImageView) findViewById(R.id.vault_logo);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);

        if (mNotifyManager != null) {
            mNotifyManager.cancel(USER_NOTIFICATION_ID);
            mNotifyManager.cancel(PASS_NOTIFICATION_ID);
        }
    }

    private void initReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(COPY_USER);
        filter.addAction(COPY_PASS);
        receiver = new VaultBCReceiver();
        registerReceiver(receiver, filter);
    }

    private void showUsernameNotification() {
        if (item != null && !item.getUsername().equals("")) {
            showNotification(COPY_USER, "username", USER_NOTIFICATION_ID);
        }
    }

    private void showPasswordNotification() {
        if (item != null && !item.getPassword().equals("")) {
            showNotification(COPY_PASS, "password", PASS_NOTIFICATION_ID);
        }
    }

    private void showNotification(String action, String type, int id) {
        Intent resultIntent = new Intent(action);
        PendingIntent resultPendingIntent = PendingIntent.getBroadcast(this, 0, resultIntent, 0);

        NotificationCompat.Builder mBuilder = (android.support.v7.app.NotificationCompat.Builder)
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_lock)
                        .setContentTitle("simpleVault")
                        .setContentText("Copy " + type + " to clipboard")
                        .setContentIntent(resultPendingIntent);

        mNotifyManager.notify(id, mBuilder.build());
    }


    private void initToolbar() {
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
        }
    }

    private void setToolbarTitle(final String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void setToolbarSubtitle(final String subtitle) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.vault_entry_toolbar, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (saved) {
            menu.findItem(R.id.vault_save).setVisible(false);
        }
        else {
            menu.findItem(R.id.vault_save).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem paramMenuItem) {
        switch (paramMenuItem.getItemId()) {
            default:
                return super.onOptionsItemSelected(paramMenuItem);

            case R.id.vault_save:
                setToolbarSubtitle("Saving...");
                save();
                break;
        }
        return true;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        saved = false;
        invalidateOptionsMenu();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGO) {
            if (resultCode == RESULT_OK) {
                String logoName = data.getStringExtra("logo");

                item.setLogoName(logoName);
                updateLogo();

                saved = false;
                invalidateOptionsMenu();
            }
        }
        else if (requestCode == REQUEST_PASSWORD) {
            if (resultCode == RESULT_OK) {
                password.setText(data.getStringExtra("password"));

                saved = false;
                invalidateOptionsMenu();
            }
        }
    }

    private void updateLogo() {
        Drawable logoDrawable = Util.getDrawableByName(this, "logo_" + item.getLogoName(), R.drawable.logo_key);
        logo.setImageDrawable(logoDrawable);
    }

    private void display() {
        String toolbarTitle = (item.getTitle().equals("")) ? "New entry" : item.getTitle();
        setToolbarTitle(toolbarTitle);
        setToolbarSubtitle("");

        updateLogo();

        title.setText(item.getTitle());
        url.setText(item.getUrl());
        username.setText(item.getUsername());
        password.setText(item.getPassword());
        group.setText(item.getGroup());
        note.setText(item.getNote());

        if (item.getPassword().equals("")) {
            passwordGenerate.setVisibility(View.VISIBLE);
            passwordCopy.setVisibility(View.GONE);
        }
        else {
            passwordGenerate.setVisibility(View.GONE);
            passwordCopy.setVisibility(View.VISIBLE);
        }
    }

    private void save() {
        String titleText = title.getText().toString().replaceAll("\\s++$", "");
        String groupText = group.getText().toString().replaceAll("\\s++$", "");
        String urlText = url.getText().toString().replaceAll("\\s++$", "");
        String userText = username.getText().toString().replaceAll("\\s++$", "");
        String passText = password.getText().toString().replaceAll("\\s++$", "");
        String noteText = note.getText().toString().replaceAll("\\s++$", "");
        Long time = System.currentTimeMillis() / 1000;
        String edit = time.toString();

        if (titleText.equals("")) {
            Toast.makeText(this, "Entry needs a title!", Toast.LENGTH_SHORT).show();
            setToolbarSubtitle("");
            return;
        }

        if (groupText.equals("")) {
            groupText = defaultGroup;
        }

        if (!urlText.equals("") && !urlText.startsWith("http://") && !urlText.startsWith("https://")) {
            urlText = "http://" + urlText;
        }

        item.setTitle(titleText);
        item.setGroup(groupText);
        item.setEdit(edit);
        item.setUrl(urlText);
        item.setUsername(userText);
        item.setPassword(passText);
        item.setEdit(String.valueOf(System.currentTimeMillis()));
        item.setNote(noteText);

        Intent i = new Intent();
        i.putExtra("item", item);
        setResult(RESULT_OK, i);
        finish();
    }

    public void openURL(View view) {
        String link = item.getUrl();
        if (link.equals("")) {
            return;
        }

        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            link = "http://" + link;
        }

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(browserIntent);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.copyuser:
                Util.copyToClipboard(VaultEntry.this, item.getUsername(), "Username copied to clipboard");
                break;

            case R.id.copypass:
                Util.copyToClipboard(VaultEntry.this, item.getPassword(), "Password copied to clipboard");
                break;

            case R.id.generatepass:
                Intent i = new Intent(getApplicationContext(), PasswordGenerator.class);
                startActivityForResult(i, REQUEST_PASSWORD);
                break;
        }
    }

    public class VaultBCReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(COPY_USER)) {
                    Util.copyToClipboard(VaultEntry.this, item.getUsername(), "Username copied to clipboard");
                }
                else if (intent.getAction().equals(COPY_PASS)) {
                    Util.copyToClipboard(VaultEntry.this, item.getPassword(), "Password copied to clipboard");
                }
            }
        }
    }
}