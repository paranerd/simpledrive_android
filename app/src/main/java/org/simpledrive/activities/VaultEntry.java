package org.simpledrive.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.models.VaultItem;


public class VaultEntry extends AppCompatActivity implements TextWatcher {
    // General
    private VaultItem item;
    private int REQUEST_ICON = 0;
    private boolean saved = true;
    private String origTitle;

    // Interface
    private EditText title;
    private EditText category;
    private ImageView icon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vaultentry);
        initToolbar();

        title = (EditText) findViewById(R.id.vault_title);
        title.addTextChangedListener(this);

        category = (EditText) findViewById(R.id.vault_category);
        category.addTextChangedListener(this);

        icon = (ImageView) findViewById(R.id.vault_icon);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), IconSelector.class);
                startActivityForResult(i, REQUEST_ICON);
            }
        });

        origTitle = getIntent().getExtras().getString("title");
        item = (origTitle != null && !origTitle.equals("")) ? Vault.getEntry(origTitle) : new VaultItem("", "", "", "", "", "", null);
        displayEntry();
        saved = true;
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
                int drawableResourceId = this.getResources().getIdentifier("logo_" + iconName, "drawable", this.getPackageName());
                Bitmap iconBmp = BitmapFactory.decodeResource(getResources(), drawableResourceId);

                item.setIcon(iconName);
                item.setIconBmp(iconBmp);

                displayEntry();
            }
        }
    }

    private void displayEntry() {
        setToolbarTitle(item.getTitle());
        setToolbarSubtitle("");
        icon.setImageBitmap(item.getIconBmp());
        title.setText(item.getTitle());
        category.setText(item.getCategory());
    }

    private void save() {
        String titleText = title.getText().toString().replaceAll("\\s++$", "");
        String categoryText = category.getText().toString().replaceAll("\\s++$", "");

        // When updating the title, check if entry with the same title already exists
        if (!origTitle.equals(titleText) && Vault.getEntry(titleText) != null) {
            Toast.makeText(this, "Entry " + titleText + " already exists!", Toast.LENGTH_SHORT).show();
            setToolbarSubtitle("");
            return;
        }

        item.setTitle(titleText);
        item.setCategory(categoryText);

        if (Vault.saveEntry(item)) {
            displayEntry();
            saved = true;
            invalidateOptionsMenu();
        }
    }
}