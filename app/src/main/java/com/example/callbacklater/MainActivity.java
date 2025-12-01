package com.example.callbacklater;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.callbacklater.MessageLogManager.Entry;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private ListView lvCallLog;
    private static final int REQ_PERMS = 101;
    private CallLogContentObserver callLogObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        initializeUI();

        // Request permissions
        requestNeededPermissions();

        // Check notification listener permission
        checkNotificationListenerPermission();

        // If permissions are already granted, load call log and register observer
        if (hasAllPermissions()) {
            loadCallLog();
            registerCallLogObserver();
        }
    }

    private void initializeUI() {
        lvCallLog = findViewById(R.id.lv_call_log);
        Button btnSettings = findViewById(R.id.btn_settings);
        Button btnLogs = findViewById(R.id.btn_logs);

        btnSettings.setOnClickListener(v -> 
            startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
            
        btnLogs.setOnClickListener(v -> 
            startActivity(new Intent(MainActivity.this, LogViewerActivity.class)));
    }

    private void checkNotificationListenerPermission() {
        // Check if notification listener is enabled
        String enabledListeners = android.provider.Settings.Secure.getString(
            getContentResolver(), "enabled_notification_listeners");

        if (enabledListeners == null || !enabledListeners.contains(getPackageName())) {
            Toast.makeText(this,
                "Please enable notification access for this app in system settings.",
                Toast.LENGTH_LONG).show();

            Intent intent = new Intent(
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void requestNeededPermissions() {
        String[] perms = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS
        };

        List<String> permsToRequest = new ArrayList<>();
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(perm);
            }
        }

        if (!permsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permsToRequest.toArray(new String[0]),
                REQ_PERMS);
        }
    }

    private boolean hasAllPermissions() {
        String[] perms = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS
        };

        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_PERMS) {
            boolean allGranted = true;
            StringBuilder deniedPerms = new StringBuilder();

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    deniedPerms.append("\n").append(permissions[i]);
                }
            }

            String message = allGranted
                ? "All permissions granted"
                : "Some permissions were denied:" + deniedPerms.toString();

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            // If all permissions are granted, load call log and register observer
            if (allGranted) {
                loadCallLog();
                registerCallLogObserver();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void loadCallLog() {
        List<HashMap<String, String>> data = new ArrayList<>();
        SimpleDateFormat sdfOutput = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Cursor cursor = null;

        try {
            // Load system call logs
            String[] projection = {
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE
            };

            String selection = android.provider.CallLog.Calls.TYPE + " IN (?,?)";
            String[] selectionArgs = new String[] {
                String.valueOf(android.provider.CallLog.Calls.MISSED_TYPE),
                String.valueOf(android.provider.CallLog.Calls.INCOMING_TYPE)
            };

            cursor = getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                android.provider.CallLog.Calls.DATE + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.CallLog.Calls.CACHED_NAME));
                    String number = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.CallLog.Calls.NUMBER));
                    int type = cursor.getInt(cursor.getColumnIndexOrThrow(
                        android.provider.CallLog.Calls.TYPE));
                    long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(
                        android.provider.CallLog.Calls.DATE));

                    String displayName = (name != null && !name.isEmpty()) ? name : number;
                    String callType = (type == android.provider.CallLog.Calls.MISSED_TYPE)
                        ? "Missed" : "Received";
                    String time = sdfOutput.format(new Date(dateMillis));

                    HashMap<String, String> map = new HashMap<>();
                    map.put("contact", displayName);
                    map.put("call_type", callType);
                    map.put("time", time);
                    map.put("timestamp", String.valueOf(dateMillis)); // Add timestamp for sorting
                    data.add(map);

                    if (type == android.provider.CallLog.Calls.MISSED_TYPE) {
                        long now = System.currentTimeMillis();
                        if (now - dateMillis <= 2000) {
                            sendAutoReplyIfNotSent(number);
                        }
                    }

                } while (cursor.moveToNext());
            }

            // Load WhatsApp calls from MessageLogManager
            List<MessageLogManager.Entry> whatsappEntries = MessageLogManager.readAll(this);
            for (MessageLogManager.Entry entry : whatsappEntries) {
                if (entry.contact.startsWith("W:")) {
                    String contact = entry.contact.substring(2); // Remove "W:" prefix
                    long timestamp = MessageLogManager.parseIsoToMillis(entry.timeIso);
                    String time = sdfOutput.format(new Date(timestamp));

                    HashMap<String, String> map = new HashMap<>();
                    map.put("contact", contact);
                    map.put("call_type", "WhatsApp Missed");
                    map.put("time", time);
                    map.put("timestamp", String.valueOf(timestamp));
                    data.add(map);
                }
            }

            // Sort the combined list by timestamp descending
            data.sort((a, b) -> Long.compare(
                Long.parseLong(b.get("timestamp")),
                Long.parseLong(a.get("timestamp"))
            ));

            String[] from = {"contact", "call_type", "time"};
            int[] to = {R.id.tv_contact, R.id.tv_call_type, R.id.tv_time};

            SimpleAdapter adapter = new SimpleAdapter(
                this,
                data,
                R.layout.list_item_call_log,
                from,
                to);

            lvCallLog.setAdapter(adapter);
            lvCallLog.setOnItemClickListener((parent, view, position, id) -> {
                // Handle item click if needed
            });

        } catch (SecurityException e) {
            Log.w("MainActivity", "Permission denied for reading call logs", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void registerCallLogObserver() {
        callLogObserver = new CallLogContentObserver(new Handler(), this);
        getContentResolver().registerContentObserver(
            android.provider.CallLog.Calls.CONTENT_URI, 
            true, 
            callLogObserver);
    }

    private void sendAutoReplyIfNotSent(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w("MainActivity", "No phone number to send auto-reply");
            return;
        }

        long now = System.currentTimeMillis();
        List<Entry> sentEntries = MessageLogManager.filterEntries(
            this, 
            phoneNumber, 
            now - 5*60*1000, 
            now);

        if (!sentEntries.isEmpty()) {
            Log.i("MainActivity", "Auto-reply SMS already sent recently to: " + phoneNumber);
            return;
        }

        SmsManager smsManager = SmsManager.getDefault();
        String message = getSharedPreferences("call_prefs", MODE_PRIVATE)
            .getString("auto_reply_msg", "User is currently unavailable or sleeping. For urgent matters, please chat via What's App. Otherwise kindly return call at 4.00 pm or anytime thereafter.");

        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            MessageLogManager.addEntry(this, phoneNumber, now);
            Log.i("MainActivity", "Sent auto-reply SMS to: " + phoneNumber);
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to send auto-reply SMS", e);
        }
    }
}