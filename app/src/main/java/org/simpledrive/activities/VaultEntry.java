package org.simpledrive.activities;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItem;

public class VaultEntry extends AppCompatActivity implements TextWatcher {
    // General
    private VaultItem item;
    private int REQUEST_ICON = 0;
    private int REQUEST_PASSWORD = 1;
    private int USER_NOTIFICATION_ID = 1;
    private int PASS_NOTIFICATION_ID = 2;
    private boolean saved = true;
    private String origTitle;

    // Notification
    private NotificationManager mNotifyManager;
    private VaultBCReceiver receiver;
    public static final String COPY_USER = "org.simpledrive.action.copyuser";
    public static final String COPY_PASS = "org.simpledrive.action.copypass";

    // Interface
    private EditText title;
    private EditText category;
    private EditText url;
    private EditText username;
    private EditText password;
    private ImageView icon;
    private Button passwordCopy;
    private Button passwordGenerate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init interface
        setContentView(R.layout.activity_vaultentry);
        initToolbar();

        title = (EditText) findViewById(R.id.vault_title);
        title.addTextChangedListener(this);

        category = (EditText) findViewById(R.id.vault_category);
        category.addTextChangedListener(this);

        url = (EditText) findViewById(R.id.vault_url);
        url.addTextChangedListener(this);

        username = (EditText) findViewById(R.id.vault_user);
        username.addTextChangedListener(this);

        password = (EditText) findViewById(R.id.vault_pass);
        password.addTextChangedListener(this);

        passwordCopy = (Button) findViewById(R.id.copypass);
        passwordGenerate = (Button) findViewById(R.id.generatepass);

        icon = (ImageView) findViewById(R.id.vault_icon);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), IconSelector.class);
                startActivityForResult(i, REQUEST_ICON);
            }
        });

        // Init receiver
        initReceiver();

        // Init entry
        item = getIntent().getParcelableExtra("item");
        if (item == null) {
            item = new VaultItem();
        }
        origTitle = item.getTitle();

        display();
        saved = true;

        // Show notifications
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showPasswordNotification();
        showUsernameNotification();
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
        if (item != null && !item.getUser().equals("")) {
            showNotification(COPY_USER, "username", USER_NOTIFICATION_ID);
        }
    }

    private void showPasswordNotification() {
        if (item != null && !item.getPass().equals("")) {
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
        if (requestCode == REQUEST_ICON) {
            if (resultCode == RESULT_OK) {
                String iconName = data.getStringExtra("icon");

                item.setIcon(iconName);
                Bitmap drawable = Util.getDrawableByName(this, "logo_" + iconName, R.drawable.logo_default);
                item.setIconBmp(drawable);
                icon.setImageBitmap(drawable);
            }
        }
        else if (requestCode == REQUEST_PASSWORD) {
            if (resultCode == RESULT_OK) {
                password.setText(data.getStringExtra("password"));
            }
        }
    }

    private void display() {
        String toolbarTitle = (item.getTitle().equals("")) ? "New entry" : item.getTitle();
        setToolbarTitle(toolbarTitle);
        setToolbarSubtitle("");

        Bitmap entryIcon = Util.getDrawableByName(this, "logo_" + item.getIcon(), R.drawable.logo_default);

        icon.setImageBitmap(entryIcon);
        title.setText(item.getTitle());
        url.setText(item.getURL());
        username.setText(item.getUser());
        password.setText(item.getPass());
        category.setText(item.getCategory());

        if (item.getPass().equals("")) {
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
        String categoryText = category.getText().toString().replaceAll("\\s++$", "");
        String urlText = url.getText().toString().replaceAll("\\s++$", "");
        String userText = username.getText().toString().replaceAll("\\s++$", "");
        String passText = password.getText().toString().replaceAll("\\s++$", "");

        if (titleText.equals("")) {
            Toast.makeText(this, "Entry needs a title!", Toast.LENGTH_SHORT).show();
            setToolbarSubtitle("");
            return;
        }
        // When updating the title, check if entry with the same title already exists
        if (!origTitle.equals(titleText) && Vault.exists(titleText)) {
            Toast.makeText(this, "Entry " + titleText + " already exists!", Toast.LENGTH_SHORT).show();
            setToolbarSubtitle("");
            return;
        }

        item.setTitle(titleText);
        item.setURL(urlText);
        item.setUser(userText);
        item.setPass(passText);
        item.setCategory(categoryText);
        item.setType("website");

        if (item.getIcon().equals("")) {
            item.setIcon("default");
        }
        item.setIconBmp(Util.getDrawableByName(this, "logo_" + item.getIcon(), R.drawable.logo_default));

        if (Vault.saveEntry(item, origTitle)) {
            display();
            saved = true;
            origTitle = titleText;
            invalidateOptionsMenu();
        }
    }

    public void openURL(View view) {
        String link = item.getURL();
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
                Util.copyToClipboard(VaultEntry.this, item.getUser(), "Username copied to clipboard");
                break;

            case R.id.copypass:
                Util.copyToClipboard(VaultEntry.this, item.getPass(), "Password copied to clipboard");
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
            if (intent.getAction().equals(COPY_USER)) {
                Util.copyToClipboard(VaultEntry.this, item.getUser(), "Username copied to clipboard");
            }
            else if (intent.getAction().equals(COPY_PASS)) {
                Util.copyToClipboard(VaultEntry.this, item.getPass(), "Password copied to clipboard");
            }
        }
    }
}