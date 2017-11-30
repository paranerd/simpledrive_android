package org.simpledrive.helper;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Permissions {
    private AppCompatActivity e;
    private int REQUEST_CODE;
    private List<String> permissionsWanted = new ArrayList<>();
    private List<String> permissionsNeeded = new ArrayList<>();

    public Permissions(AppCompatActivity e, int requestCode) {
        this.e = e;
        this.REQUEST_CODE = requestCode;
    }

    public interface TaskListener {
        void onPositive();
        void onNegative();
    }

    public void wantStorage() {
        this.permissionsWanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        addPermissionIfNeeded(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void addPermissionIfNeeded(String permission) {
        if (ContextCompat.checkSelfPermission(e, permission) != PackageManager.PERMISSION_GRANTED || ActivityCompat.shouldShowRequestPermissionRationale(e, permission)) {
            this.permissionsNeeded.add(permission);
        }
    }

    public void request(final String title, final String explanation, final TaskListener listener) {
        if (this.permissionsWanted.size() == 0) {
            return;
        }

        // App has all the wanted permissions
        if (this.permissionsNeeded.size() == 0) {
            if (listener != null) {
                listener.onPositive();
                return;
            }
        }

        // Ask for permissions
        for (int i = 0; i < this.permissionsNeeded.size(); i++) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(e, permissionsNeeded.get(i))) {
                new android.support.v7.app.AlertDialog.Builder(e)
                        .setTitle(title)
                        .setMessage(explanation)
                        .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(e, permissionsWanted.toArray(new String[permissionsWanted.size()]), REQUEST_CODE);
                            }

                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (listener != null) {
                                    listener.onNegative();
                                }
                            }
                        })
                        .show();
            }
            else {
                ActivityCompat.requestPermissions(e, permissionsWanted.toArray(new String[permissionsWanted.size()]), REQUEST_CODE);
            }
        }
    }
}
