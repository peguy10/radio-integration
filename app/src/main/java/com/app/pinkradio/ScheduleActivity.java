package com.app.pinkradio;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class ScheduleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);


        WebView webView = findViewById(R.id.webview_schedule);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Activer JavaScript si nécessaire
        webView.loadUrl("https://www.radiointegration.com/schedule/");

    }
}