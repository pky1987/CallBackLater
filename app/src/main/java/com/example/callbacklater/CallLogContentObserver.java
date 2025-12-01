package com.example.callbacklater;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import android.Manifest;
import android.content.pm.PackageManager;

import com.example.callbacklater.MessageLogManager;

import android.telephony.SmsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CallLogContentObserver extends ContentObserver {

    private Context context;
    private static final String TAG = "CallLogObserver";
    private static final long SMS_SEND_WINDOW_MS = 3000; // 3 seconds

    public CallLogContentObserver(Handler handler, Context ctx) {
        super(handler);
        this.context = ctx;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        checkForRecentMissedCallAndSendSms();
    }

    private void checkForRecentMissedCallAndSendSms() {
        // Check permission READ_CALL_LOG
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted");
            return;
        }
        // Check SEND_SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted");
            return;
        }

        Cursor cursor = null;
        try {
            Log.i(TAG, "Checking for recent missed calls to send SMS...");
            String[] projection = new String[]{
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
            };
            String selection = CallLog.Calls.TYPE + " = ?";
            String[] selectionArgs = new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)};
            String sortOrder = CallLog.Calls.DATE + " DESC LIMIT 1";

            cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder);

            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                long now = System.currentTimeMillis();

                Log.i(TAG, "Most recent missed call number: " + number + ", dateMillis: " + dateMillis + ", now: " + now);

                if (now - dateMillis <= SMS_SEND_WINDOW_MS) {
                    // Check if already sent SMS for this number and timestamp
                    List<MessageLogManager.Entry> sentEntries = MessageLogManager.filterEntries(context, number, dateMillis, now);
                    if (sentEntries.isEmpty()) {
                        Log.i(TAG, "Sending auto-reply SMS to number: " + number);
                        sendAutoReplySms(number);
                    } else {
                        Log.i(TAG, "Auto-reply SMS already sent recently to: " + number);
                    }
                } else {
                    Log.i(TAG, "Missed call is older than SMS send window. No SMS sent.");
                }
            } else {
                Log.i(TAG, "No missed calls found in call log.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check/send missed call SMS", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void sendAutoReplySms(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w(TAG, "No phone number to send auto-reply");
            return;
        }

        SmsManager smsManager = SmsManager.getDefault();

        // Read auto reply message from SharedPreferences
        String message = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
                .getString("auto_reply_msg", "User is currently unavailable or sleeping. For urgent matters, please chat via What's App. Otherwise kindly return call at 4.00 pm or anytime thereafter.");

        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            MessageLogManager.addEntry(context, phoneNumber, System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "Failed to send auto-reply SMS", e);
        }
    }
}
