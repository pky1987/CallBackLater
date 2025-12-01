package com.example.callbacklater;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class LogViewerActivity extends AppCompatActivity {
    private static final String TAG = "LogViewerActivity";

    private EditText etFilterContact;
    private Button btnStartDate, btnEndDate, btnApplyFilter, btnClearFilter, btnExport;
    private ListView listView;

    private Long filterStartDateMillis = null;
    private Long filterEndDateMillis = null;
    private String filterContact = "";

    private List<MessageLogManager.Entry> currentEntries = new ArrayList<>();
    private LogEntryAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        etFilterContact = findViewById(R.id.et_filter_contact);
        btnStartDate = findViewById(R.id.btn_start_date);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnApplyFilter = findViewById(R.id.btn_apply_filter);
        btnClearFilter = findViewById(R.id.btn_clear_filter);
        listView = findViewById(R.id.list_logs);
        btnExport = findViewById(R.id.btn_export_logs);

        loadAllLogs();

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));

        btnApplyFilter.setOnClickListener(v -> applyFilter());
        btnClearFilter.setOnClickListener(v -> clearFilter());

        btnExport.setOnClickListener(v -> shareLogFile());

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDeleteEntry(position);
            return true;
        });
    }

    private void loadAllLogs() {
        currentEntries = MessageLogManager.readAll(this);
        updateListView(currentEntries);
        btnStartDate.setText("Start Date");
        btnEndDate.setText("End Date");
    }

    private void updateListView(List<MessageLogManager.Entry> entries) {
        adapter = new LogEntryAdapter(this, entries);
        listView.setAdapter(adapter);
    }

    private void showDatePicker(boolean isStart) {
        final Calendar c = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, dayOfMonth, 0, 0, 0);
            long millis = chosen.getTimeInMillis();

            if (isStart) {
                filterStartDateMillis = millis;
                btnStartDate.setText(year + "-" + (month + 1) + "-" + dayOfMonth);
            } else {
                filterEndDateMillis = millis;
                btnEndDate.setText(year + "-" + (month + 1) + "-" + dayOfMonth);
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    private void applyFilter() {
        filterContact = etFilterContact.getText().toString();
        currentEntries = MessageLogManager.filterEntries(this, filterContact, filterStartDateMillis, filterEndDateMillis);
        updateListView(currentEntries);
    }

    private void clearFilter() {
        filterContact = "";
        filterStartDateMillis = null;
        filterEndDateMillis = null;

        etFilterContact.setText("");
        btnStartDate.setText("Start Date");
        btnEndDate.setText("End Date");

        loadAllLogs();
    }

    private void confirmDeleteEntry(int position) {
        if (position < 0 || position >= currentEntries.size()) return;

        MessageLogManager.Entry entry = currentEntries.get(position);

        new AlertDialog.Builder(this)
            .setTitle("Delete log entry")
            .setMessage("Delete selected log entry?\n" + entry.timeIso + " — " + entry.contact)
            .setPositiveButton("Delete", (dialog, which) -> {
                boolean deleted = MessageLogManager.deleteEntry(LogViewerActivity.this, entry);
                if (deleted) {
                    Toast.makeText(LogViewerActivity.this, "Entry deleted", Toast.LENGTH_SHORT).show();
                    applyFilter(); // Refresh list
                } else {
                    Toast.makeText(LogViewerActivity.this, "Failed to delete entry", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void shareLogFile() {
        try {
            File src = new File(getFilesDir(), "message_log.xml");
            if (!src.exists()) {
                Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Copy to cache so we can share via FileProvider
            File dst = new File(getCacheDir(), "message_log.xml");
            copyFile(src, dst);

            Uri uri = FileProvider.getUriForFile(this, "com.example.callbacklater.fileprovider", dst);

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/xml");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share message log"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to share log file", e);
            Toast.makeText(this, "Failed to share log", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst, false);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }
}
