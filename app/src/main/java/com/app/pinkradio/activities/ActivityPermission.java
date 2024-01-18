package com.app.pinkradio.activities;

import static com.app.pinkradio.utils.Constant.PERMISSIONS_REQUEST;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.pinkradio.R;
import com.app.pinkradio.utils.Utils;

public class ActivityPermission extends AppCompatActivity {

    Button btnAllowPermission;
    Button btnLater;
    TextView txtPermissionMessage;
    ScrollView scrollView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_permission);

        btnAllowPermission = findViewById(R.id.btn_allow_permission);
        txtPermissionMessage = findViewById(R.id.txt_permission_message);
        scrollView = findViewById(R.id.scroll_view);

        btnAllowPermission.setOnClickListener(v -> Utils.requestPermission(ActivityPermission.this));
        txtPermissionMessage.setText(Html.fromHtml(getString(R.string.permission_message)));

        btnLater = findViewById(R.id.btn_later);
        btnLater.setOnClickListener(v -> finish());

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                finishAffinity();
            }
        }
    }

}
