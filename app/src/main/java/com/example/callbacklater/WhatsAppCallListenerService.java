package com.example.callbacklater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.example.callbacklater.MessageLogManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppCallListenerService extends NotificationListenerService {
    private static final String TAG = "WhatsAppCallListenerService";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_CALL_REGEX = "(\\+?\\d[\\d\\s-]{5,}\\d)";
    private static final long SMS_SEND_WINDOW_MS = 5000; // 5 seconds window to send message after notification
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "whatsapp_call_listener_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildForegroundNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "WhatsApp Call Listener";
            String description = "Notification channel for WhatsApp call listener foreground service";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent = new Intent(this, com.example.callbacklater.MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WhatsApp Call Listener Active")
                .setContentText("Listening for missed WhatsApp calls")
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        try {
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

            if (title == null || text == null) {
                Log.w(TAG, "W: Notification missing required fields");
                return;
            }

            String titleStr = title.toString();
            String textStr = text.toString().toLowerCase();

            // Detect WhatsApp call patterns
            boolean isCall = titleStr.contains("WhatsApp") ||
                    textStr.contains("calling") ||
                    textStr.contains("missed call");

            if (!isCall) return;

            // Extract call type and status
            boolean isMissedCall = textStr.contains("missed call");
            boolean isAudioCall = textStr.contains("audio call") ||
                    textStr.contains("voice call");
            boolean isVideoCall = textStr.contains("video call");

            if (isMissedCall && (isAudioCall || isVideoCall)) {
                String senderNameOrNumber = extractPhoneNumberFromNotification(notification);
                if (senderNameOrNumber == null || senderNameOrNumber.isEmpty()) {
                    senderNameOrNumber = titleStr; // fallback to title as sender identifier
                }

                Log.i(TAG, "W: Missed WhatsApp call detected from: " + senderNameOrNumber);
                long now = System.currentTimeMillis();
                String contactWithTag = "W:" + senderNameOrNumber;

                // Log the missed call
                MessageLogManager.addEntry(getApplicationContext(), contactWithTag, now);
                Log.i(TAG, "W: Logged WhatsApp missed call entry for: " + contactWithTag);

                // Send message to WhatsApp chat interface with auto-reply
                String autoReply = getApplicationContext().getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
                        .getString("auto_reply_msg", "User is currently unavailable or sleeping. For urgent matters, please chat via What's App. Otherwise kindly return call at 4.00 pm or anytime thereafter.");
                sendWhatsAppMessage(senderNameOrNumber, autoReply);
            }
        } catch (Exception e) {
            Log.e(TAG, "W: Error processing WhatsApp notification", e);
        }
    }

    private String extractPhoneNumberFromNotification(Notification notification) {
        if (notification == null) return null;

        try {
            // Try to extract from text first
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null) {
                String textStr = text.toString().trim();
                if (!textStr.isEmpty()) {
                    Pattern pattern = Pattern.compile(WHATSAPP_CALL_REGEX);
                    Matcher matcher = pattern.matcher(textStr);
                    if (matcher.find()) {
                        return matcher.group(1).replaceAll("[\\s-]", "");
                    }
                }
            }

            // Try to extract from title if text doesn't contain number
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null) {
                String titleStr = title.toString().trim();
                if (!titleStr.isEmpty() && titleStr.matches(WHATSAPP_CALL_REGEX)) {
                    return titleStr.replaceAll("[\\s-]", "");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "W: Error extracting phone number from notification", e);
        }
        return null;
    }

    private void sendWhatsAppMessage(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w(TAG, "W: No phone number to send WhatsApp message");
            return;
        }

        try {
            Context context = getApplicationContext();
            // Normalize phone number to digits only removing spaces, dashes, or other chars except plus sign
            String normalizedNumber = phoneNumber.replaceAll("[^\\d+]", "");
            String encodedNumber = Uri.encode(normalizedNumber);
            String encodedMessage = Uri.encode(message);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("https://wa.me/" + encodedNumber + "?text=" + encodedMessage);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Log.i(TAG, "W: Opened WhatsApp chat interface for number: " + encodedNumber + " with message: " + message);
                // Record the sent message in the message log
                try {
                    MessageLogManager.addEntry(context, "W:" + phoneNumber, System.currentTimeMillis());
                } catch (Exception e) {
                    Log.w(TAG, "W: Failed to log sent message", e);
                }
            } else {
                Log.w(TAG, "W: WhatsApp app not installed");
            }
        } catch (Exception e) {
            Log.e(TAG, "W: Failed to open WhatsApp chat interface", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            Log.i(TAG, "W: WhatsApp notification removed: " + sbn.getKey());
        }
    }
}
