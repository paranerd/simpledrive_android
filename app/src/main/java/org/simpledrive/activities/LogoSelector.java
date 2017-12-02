package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import org.simpledrive.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogoSelector extends AppCompatActivity {
    // General
    private List<Map<String, Object>> logos = new ArrayList<>();

    // Interface
    private ListView list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_logoselector);

        initList();
        initToolbar();
        displayLogos();
    }

    public void onBackPressed() {
        Intent returnIntent = new Intent();
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    private void initList() {
        list = (ListView) findViewById(R.id.listview);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent();
                i.putExtra("logo", logos.get(position).get("name").toString());
                setResult(RESULT_OK, i);
                finish();
            }
        });
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

    private void displayLogos() {
        // Add all drawable logos
        Field[] drawablesFields = R.drawable.class.getFields();

        for (Field field : drawablesFields) {
            if (field.getName().indexOf("logo_") == 0) {
                HashMap<String, Object> datum = new HashMap<>();
                int drawableResourceId = this.getResources().getIdentifier(field.getName(), "drawable", this.getPackageName());
                datum.put("logo", drawableResourceId);
                datum.put("name", field.getName().substring(5));
                datum.put("type", "logo");
                logos.add(datum);
            }
        }

        list.setAdapter(new SimpleAdapter(this, logos, R.layout.listview_detail, new String[] {"logo", "name", "type"}, new int[] {R.id.thumb, R.id.title, R.id.detail1}));
    }
}