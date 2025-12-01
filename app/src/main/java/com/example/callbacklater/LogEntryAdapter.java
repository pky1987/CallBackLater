package com.example.callbacklater;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class LogEntryAdapter extends ArrayAdapter<MessageLogManager.Entry> {
    private final SimpleDateFormat isoFormat;
    private final SimpleDateFormat indianDateFormat;

    public LogEntryAdapter(Context context, List<MessageLogManager.Entry> entries) {
        super(context, 0, entries);
        isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        indianDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        indianDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = convertView;
        if (itemView == null) {
            itemView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_call_log, parent, false);
        }

        MessageLogManager.Entry entry = getItem(position);

        TextView tvContact = itemView.findViewById(R.id.tv_contact);
        TextView tvCallType = itemView.findViewById(R.id.tv_call_type);
        TextView tvTime = itemView.findViewById(R.id.tv_time);

        if (entry != null) {
            String contactRaw = entry.contact != null ? entry.contact : "";

            // Check if contact has WhatsApp tag "W:"
            String callType;
            String contactDisplay = contactRaw;
            if (contactRaw.startsWith("W:")) {
                callType = "WhatsApp Call";
                contactDisplay = contactRaw.substring(2); // Remove "W:" prefix
            } else {
                callType = "Phone Call / SMS";
            }

            tvContact.setText(contactDisplay);
            tvCallType.setText(callType);

            // Parse ISO time and convert to Indian timezone display
            String timeDisplay = entry.timeIso;
            try {
                Date date = isoFormat.parse(entry.timeIso);
                if (date != null) {
                    timeDisplay = indianDateFormat.format(date);
                }
            } catch (ParseException e) {
                // fallback to original
            }
            tvTime.setText(timeDisplay);
        }

        return itemView;
    }

    private void sendWhatsAppMessage(String phoneNumber) {
        try {
            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.whatsapp");
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "WhatsApp not installed or number invalid", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendSmsMessage(String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + phoneNumber));
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Unable to send SMS", Toast.LENGTH_SHORT).show();
        }
    }
}
