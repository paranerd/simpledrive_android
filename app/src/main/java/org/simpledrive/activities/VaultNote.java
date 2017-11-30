package org.simpledrive.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.helper.Preferences;
import org.simpledrive.helper.Util;
import org.simpledrive.models.VaultItemNote;

public class VaultNote extends AppCompatActivity implements TextWatcher {
    // General
    private VaultItemNote item;
    private int REQUEST_LOGO = 0;
    private boolean saved = true;
    private int id;

    // Interface
    private EditText title;
    private EditText category;
    private EditText content;
    private TextView info;
    private ImageView logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);

        initInterface();
        initToolbar();

        // Init entry
        id = getIntent().getIntExtra("id", -1);
        item = getIntent().getParcelableExtra("item");
        if (item == null) {
            item = new VaultItemNote();
        }

        display();
        saved = true;
    }

    private void initInterface() {
        // Set theme
        int theme = (Preferences.getInstance(this).read(Preferences.TAG_COLOR_THEME, "light").equals("light")) ? R.style.MainTheme_Light : R.style.MainTheme_Dark;
        setTheme(theme);

        // Init interface
        setContentView(R.layout.activity_vault_note);

        title = (EditText) findViewById(R.id.vault_title);
        title.addTextChangedListener(this);

        category = (EditText) findViewById(R.id.vault_category);
        category.addTextChangedListener(this);

        content = (EditText) findViewById(R.id.vault_content);
        content.addTextChangedListener(this);

        FrameLayout logo_wrapper = (FrameLayout) findViewById(R.id.logo_wrapper);
        logo_wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), LogoSelector.class);
                startActivityForResult(i, REQUEST_LOGO);
            }
        });

        info = (TextView) findViewById(R.id.vault_info);
        logo = (ImageView) findViewById(R.id.vault_logo);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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

                item.setLogo(logoName);
                updateLogo();

                saved = false;
                invalidateOptionsMenu();
            }
        }
    }

    private void updateLogo() {
        Bitmap logoBmp = Util.getDrawableByName(this, "logo_" + item.getLogo(), R.drawable.logo_);
        logo.setImageBitmap(logoBmp);

        if (item.getLogo().equals("")) {
            info.setVisibility(View.VISIBLE);
            logo.setVisibility(View.GONE);

        }
        else {
            info.setVisibility(View.GONE);
            logo.setVisibility(View.VISIBLE);
        }
    }

    private void display() {
        String toolbarTitle = (item.getTitle().equals("")) ? "New entry" : item.getTitle();
        setToolbarTitle(toolbarTitle);
        setToolbarSubtitle("");

        updateLogo();

        title.setText(item.getTitle());
        category.setText(item.getCategory());
        content.setText(item.getContent());
    }

    private void save() {
        String titleText = title.getText().toString().replaceAll("\\s++$", "");
        String categoryText = category.getText().toString().replaceAll("\\s++$", "");
        String contentText = content.getText().toString();

        if (titleText.equals("")) {
            Toast.makeText(this, "Entry needs a title!", Toast.LENGTH_SHORT).show();
            setToolbarSubtitle("");
            return;
        }

        item.setTitle(titleText);
        item.setCategory(categoryText);
        item.setType("note");
        item.setContent(contentText);
        item.setEdit(String.valueOf(System.currentTimeMillis()));

        Intent i = new Intent();
        i.putExtra("id", id);
        i.putExtra("item", item);
        setResult(RESULT_OK, i);
        finish();
    }
}