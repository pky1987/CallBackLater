package com.example.callbacklater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.example.callbacklater.MessageLogManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class EnhancedCallReceiver extends BroadcastReceiver {
    private static final String[] SPAM_KEYWORDS = {"SPAM", "SCAM", "TELEMARKETING"};
    private static final String SPAM_CHANNEL_ID = "SPAM_CALL_CHANNEL";
    private static final String PREFS_NAME = "call_prefs";
    private static final String KEY_INCOMING = "last_incoming";
    private static final String KEY_RING_TS = "ring_ts";
    private static final String KEY_ANSWERED = "answered";
    private static final String KEY_AUTO_REPLY = "auto_reply_msg";
    private static final String KEY_WHITELIST_ONLY = "whitelist_only";
    private static final long MISSED_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) {
            // In some cases state might be in "state" key, fallback
            state = intent.getStringExtra("state");
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String phoneNumber = null;
            if (intent.getExtras() != null) {
                phoneNumber = intent.getExtras().getString(
                        TelephonyManager.EXTRA_INCOMING_NUMBER);
            }

            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                Log.w("EnhancedCallReceiver", "Incoming phone number is null or empty at RINGING state");
            } else {
                Log.i("EnhancedCallReceiver", "Incoming phone number at RINGING: " + phoneNumber);
            }

            // store ringing state
            prefs.edit()
                    .putString(KEY_INCOMING, phoneNumber)
                    .putLong(KEY_RING_TS, System.currentTimeMillis())
                    .putBoolean(KEY_ANSWERED, false)
                    .apply();

            boolean isSpam = isLikelySpam(phoneNumber, context);

            if (!isSpam) {
                Intent serviceIntent = new Intent(context, CallbackService.class);
                serviceIntent.putExtra("phoneNumber", phoneNumber);
                try {
                    ContextCompat.startForegroundService(context, serviceIntent);
                } catch (Exception e) {
                    Log.w("EnhancedCallReceiver", "Failed to start CallbackService", e);
                }
            } else {
                handleSpamCall(context, phoneNumber);
            }

            return;
        }

        // Call was answered (OFFHOOK)
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            prefs.edit().putBoolean(KEY_ANSWERED, true).apply();
            Log.i("EnhancedCallReceiver", "Call state changed to OFFHOOK - answered");
            return;
        }

        // Call ended / idle: check if we recently had a ringing without answer
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            String lastIncoming = prefs.getString(KEY_INCOMING, null);
            long ringTs = prefs.getLong(KEY_RING_TS, 0);
            boolean answered = prefs.getBoolean(KEY_ANSWERED, false);

            // clear stored state
            prefs.edit().remove(KEY_INCOMING).remove(KEY_RING_TS).remove(KEY_ANSWERED).apply();

            if (lastIncoming != null && !answered) {
                Log.i("EnhancedCallReceiver", "Missed call detected from: " + lastIncoming);
                String autoReply = prefs.getString(KEY_AUTO_REPLY, "User is currently unavailable or sleeping. For urgent matters, please chat via What's App. Otherwise kindly return call at 4.00 pm or anytime thereafter.");
                boolean whitelistOnly = prefs.getBoolean(KEY_WHITELIST_ONLY, false);

                // Send auto-reply SMS to all missed callers immediately (ignore whitelist setting)
                sendAutoReplySms(context, lastIncoming, autoReply);
            } else {
                Log.i("EnhancedCallReceiver", "No missed call to process or call was answered.");
            }
        }
    }

    private boolean isLikelySpam(String phoneNumber, Context context) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return false;

        String normalized = PhoneNumberUtils.normalizeNumber(phoneNumber);
        if (normalized == null) normalized = phoneNumber;

        // Heuristic 1: contains spam keywords
        String upper = (phoneNumber == null) ? "" : phoneNumber.toUpperCase(Locale.ROOT);
        for (String kw : SPAM_KEYWORDS) {
            if (upper.contains(kw)) return true;
        }

        // Heuristic 2: suspicious patterns (short numbers, repeated digit sequences)
        String digits = normalized.replaceAll("\\D+", "");
        if (digits.length() > 0) {
            if (digits.length() <= 4) return true; // very short numbers often are service codes
            // repeated digits like 0000000 or 1111111
            if (digits.matches("^(\\d)\\1{5,}$")) return true;
        }

        // Heuristic 3: placeholder for platform spam API — keep conservative default
        return false;
    }

    private void handleSpamCall(Context context, String phoneNumber) {
        Log.d("CALL_PROTECTION", "Blocked spam call from: " + phoneNumber);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Create channel on O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    SPAM_CHANNEL_ID,
                    "Spam Calls",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications for blocked spam calls");
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SPAM_CHANNEL_ID)
                .setContentTitle("Blocked Spam Call")
                .setContentText("Call from " + (phoneNumber == null ? "unknown" : phoneNumber) + " blocked as spam")
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL);

        nm.notify(1001, builder.build());
    }

    private void sendWhatsAppMessage(Context context, String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w("EnhancedCallReceiver", "No phone number to send WhatsApp message");
            return;
        }

        try {
            // Normalize phone number to digits only removing spaces, dashes, or other chars except plus sign
            String normalizedNumber = phoneNumber.replaceAll("[^\\d+]", "");
            String encodedNumber = Uri.encode(normalizedNumber);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("https://wa.me/" + encodedNumber + "?text=" + Uri.encode(message));
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Log.i("EnhancedCallReceiver", "Opened WhatsApp chat interface for number: " + encodedNumber + " with message: " + message);
                // Record the sent message in the message log
                try {
                    MessageLogManager.addEntry(context, "W:" + phoneNumber, System.currentTimeMillis());
                } catch (Exception e) {
                    Log.w("EnhancedCallReceiver", "Failed to log sent message", e);
                }
            } else {
                Log.w("EnhancedCallReceiver", "WhatsApp app not installed");
            }
        } catch (Exception e) {
            Log.w("EnhancedCallReceiver", "Failed to open WhatsApp chat interface", e);
        }
    }

    private void sendAutoReplySms(Context context, String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w("EnhancedCallReceiver", "No phone number to send auto-reply");
            return;
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("EnhancedCallReceiver", "SEND_SMS permission not granted — cannot send auto-reply");
            // Optionally notify user or take other action here
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.i("EnhancedCallReceiver", "Sent auto-reply SMS to " + phoneNumber);
            // Record the sent message in the message log
            try {
                MessageLogManager.addEntry(context, phoneNumber, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w("EnhancedCallReceiver", "Failed to log sent message", e);
            }
        } catch (Exception e) {
            Log.w("EnhancedCallReceiver", "Failed to send auto-reply SMS", e);
        }
    }

    private boolean isNumberInContacts(Context context, String phoneNumber) {
        if (phoneNumber == null) return false;
        try {
            android.net.Uri uri = android.net.Uri.withAppendedPath(
                    android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(phoneNumber));
            String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup._ID};
            android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                boolean found = cursor.getCount() > 0;
                cursor.close();
                return found;
            }
        } catch (Exception e) {
            Log.w("EnhancedCallReceiver", "Error checking contacts", e);
        }
        return false;
    }
}
