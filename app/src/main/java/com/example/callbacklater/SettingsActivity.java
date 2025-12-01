package com.example.callbacklater;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "call_prefs";
    private static final String KEY_AUTO_REPLY = "auto_reply_msg";
    private static final String KEY_WHITELIST_ONLY = "whitelist_only";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final EditText etMessage = findViewById(R.id.et_auto_reply);
        final Switch swWhitelist = findViewById(R.id.sw_whitelist);
        Button btnSave = findViewById(R.id.btn_save_settings);

        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        etMessage.setText(prefs.getString(KEY_AUTO_REPLY, "User is currently unavailable or sleeping. For urgent matters, please chat via What's App. Otherwise kindly return call at 4.00 pm or anytime thereafter."));
        swWhitelist.setChecked(prefs.getBoolean(KEY_WHITELIST_ONLY, false));

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                    .putString(KEY_AUTO_REPLY, etMessage.getText().toString())
                    .putBoolean(KEY_WHITELIST_ONLY, swWhitelist.isChecked())
                    .apply();
                Toast.makeText(SettingsActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
