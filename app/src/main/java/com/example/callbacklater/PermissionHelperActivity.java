package com.example.callbacklater;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelperActivity extends AppCompatActivity {
    private static final int REQ_PERMS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_helper);

        Button btnRequest = findViewById(R.id.btn_request_perms);
        btnRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestNeededPermissions();
            }
        });
    }

    private void requestNeededPermissions() {
        String[] perms = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.POST_NOTIFICATIONS
        };

        // Display rationale if needed
        boolean shouldExplain = false;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                shouldExplain = true;
                break;
            }
        }

        if (shouldExplain) {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Permissions Required")
                   .setMessage("This app needs SMS, Contacts, Notifications, and Phone State permissions to send auto-reply messages for missed calls and listen to notifications.")
                   .setPositiveButton("OK", (dialog, which) -> {
                       ActivityCompat.requestPermissions(PermissionHelperActivity.this, perms, REQ_PERMS);
                   })
                   .setNegativeButton("Cancel", (dialog, which) -> {
                       Toast.makeText(PermissionHelperActivity.this, "Permissions denied. App may not work correctly.", Toast.LENGTH_LONG).show();
                   })
                   .create()
                   .show();
        } else {
            ActivityCompat.requestPermissions(this, perms, REQ_PERMS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndShowPermissionsStatus();
    }

    private void checkAndShowPermissionsStatus() {
        StringBuilder status = new StringBuilder("Current Permission Status:\n");
        status.append("SEND_SMS: ").append(ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED").append("\n");
        status.append("READ_CONTACTS: ").append(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED").append("\n");
        status.append("POST_NOTIFICATIONS: ").append(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED").append("\n");
        status.append("READ_PHONE_STATE: ").append(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED").append("\n");

        Toast.makeText(this, status.toString(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            Toast.makeText(this, allGranted ? "Permissions granted" : "Some permissions denied", Toast.LENGTH_SHORT).show();
            if (allGranted) finish();
        }
    }
}
