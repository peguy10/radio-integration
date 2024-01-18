package com.app.pinkradio.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.app.pinkradio.Config;
import com.app.pinkradio.R;
import com.app.pinkradio.models.Radio;
import com.app.pinkradio.utils.Constant;

public class ActivitySplash extends AppCompatActivity {

    ProgressBar progressBar;
    Radio radio = new Radio();
    long id = System.currentTimeMillis();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        addRadios();
        startMainActivity();
    }

    private void addRadios(){
        new Handler().postDelayed(() -> {
            radio.setRadio_image_url(Config.RADIO_IMAGE_URL);
            radio.setRadio_genre(Config.RADIO_GENRE);
            radio.setRadio_id(id);
            radio.setRadio_url(Config.RADIO_STREAM_URL);
            radio.setRadio_name(Config.RADIO_NAME);
            Constant.item_radio.add(0, radio);

            startMainActivity();
        }, 100);

    }

    private void startMainActivity() {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        }, Config.SPLASH_DURATION);
    }

}
