package com.example.callbacklater;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class CallbackService extends Service {
    private static final String TAG = "CallbackService";
    private static final String CHANNEL_ID = "CALLBACK_SERVICE_CHANNEL";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phoneNumber = null;
        if (intent != null) phoneNumber = intent.getStringExtra("phoneNumber");

        Log.i(TAG, "CallbackService started for: " + phoneNumber);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Callback Service")
                .setContentText(phoneNumber == null ? "Handling call" : "Handling call from " + phoneNumber)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1111, notification);

        // For this skeleton service we simply stop right away.
        stopSelf();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Callback Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Channel for callback foreground service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
