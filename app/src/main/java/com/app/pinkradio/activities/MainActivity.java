package com.app.pinkradio.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.app.pinkradio.BuildConfig;
import com.app.pinkradio.Config;
import com.app.pinkradio.PlaylistActivity;
import com.app.pinkradio.R;
import com.app.pinkradio.ScheduleActivity;
import com.app.pinkradio.models.Radio;
import com.app.pinkradio.services.RadioPlayerService;
import com.app.pinkradio.utils.Constant;
import com.app.pinkradio.utils.SharedPref;
import com.app.pinkradio.utils.SleepTimeReceiver;
import com.app.pinkradio.utils.Utils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.makeramen.roundedimageview.RoundedImageView;
import com.next.androidintentlibrary.BrowserIntents;
import com.next.androidintentlibrary.PhoneIntents;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import eu.gsottbauer.equalizerview.EqualizerView;
import jp.wasabeef.glide.transformations.BlurTransformation;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private final static String SELECTED_TAG = "selected_index";
    ActionBarDrawerToggle actionBarDrawerToggle;
    private DrawerLayout drawerLayout;
    NavigationView navigationView;
    ProgressBar progressBar;
    RoundedImageView imgRadioLarge;
    RoundedImageView imgAlbumArtLarge;
    ImageView imgMusicBackground;
    MaterialButton fabPlayExpand;
    TextView txtRadioExpand, txtRadioMusicSong, txtSongExpand;
    EqualizerView equalizerView;
    Utils utils;

    private Toolbar toolbar;
    LinearLayout lytExit;
    View lytDialog;
    ArrayList<Radio> radios;
    SharedPref sharedPref;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Config.ENABLE_RTL_MODE) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        sharedPref = new SharedPref(this);
        sharedPref.setCheckSleepTime();

        utils = new Utils(this);
        radios = Constant.item_radio;

        navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(this);

        drawerLayout = findViewById(R.id.drawer_layout);

        if ((ContextCompat.checkSelfPermission(MainActivity.this, "android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED)) {
            Menu menu = navigationView.getMenu();
            MenuItem menuItem = menu.findItem(R.id.drawer_permission);
            menuItem.setVisible(false);
        }

        if (savedInstanceState != null) {
            navigationView.getMenu().getItem(savedInstanceState.getInt(SELECTED_TAG)).setChecked(true);
            return;
        }

        initComponent();
        initExitDialog();
        displayData();

        toolbar = findViewById(R.id.toolbar);
        setupToolbar();
        setupNavigationDrawer(toolbar);

        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert am != null;

        SeekBar volumeSeekBar = findViewById(R.id.volumeSeekBar);
        volumeSeekBar.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeSeekBar.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                try {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, i, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, i, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    private void setupToolbar() {
        toolbar.setTitle(getString(R.string.app_name));
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_timer) {
            if (sharedPref.getIsSleepTimeOn()) {
                openTimeDialog();
            } else {
                openTimeSelectDialog();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void displayData() {
        fabPlayExpand.setOnClickListener(view -> {
            if (!utils.isNetworkAvailable()) {
                Toast.makeText(MainActivity.this, getResources().getString(R.string.internet_not_connected), Toast.LENGTH_SHORT).show();
            } else {
                Radio radio = radios.get(0);
                final Intent intent = new Intent(MainActivity.this, RadioPlayerService.class);

                if (RadioPlayerService.getInstance() != null) {
                    Radio playerCurrentRadio = RadioPlayerService.getInstance().getPlayingRadioStation();
                    if (playerCurrentRadio != null) {
                        if (radio.getRadio_id() != RadioPlayerService.getInstance().getPlayingRadioStation().getRadio_id()) {
                            RadioPlayerService.getInstance().initializeRadio(MainActivity.this, radio);
                            intent.setAction(RadioPlayerService.ACTION_PLAY);
                        } else {
                            intent.setAction(RadioPlayerService.ACTION_TOGGLE);
                        }
                    } else {
                        RadioPlayerService.getInstance().initializeRadio(MainActivity.this, radio);
                        intent.setAction(RadioPlayerService.ACTION_PLAY);
                    }
                } else {
                    RadioPlayerService.createInstance().initializeRadio(MainActivity.this, radio);
                    intent.setAction(RadioPlayerService.ACTION_PLAY);
                }
                startService(intent);

//                if (!Constant.is_playing) {
//                   //TODO
//                }

            }
        });

        if (Config.AUTOPLAY) {
            if (utils.isNetworkAvailable()) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> fabPlayExpand.performClick(), Constant.DELAY_PERFORM_CLICK);
            }
        }

    }

    public void initComponent() {

        imgMusicBackground = findViewById(R.id.img_music_background);

        equalizerView = findViewById(R.id.equalizer);
        progressBar = findViewById(R.id.progress_bar);

        imgRadioLarge = findViewById(R.id.img_radio_large);
        imgAlbumArtLarge = findViewById(R.id.img_album_art_large);

        if (Config.CIRCULAR_IMAGE_ALBUM_ART) {
            imgRadioLarge.setOval(true);
            imgAlbumArtLarge.setOval(true);
        } else {
            imgRadioLarge.setOval(false);
            imgAlbumArtLarge.setOval(false);
        }

        fabPlayExpand = findViewById(R.id.fab_play);
        txtRadioExpand = findViewById(R.id.txt_radio_name_expand);
        txtSongExpand = findViewById(R.id.txt_metadata_expand);

        if (!utils.isNetworkAvailable()) {
            txtRadioExpand.setText(getResources().getString(R.string.app_name));
            txtSongExpand.setText(getResources().getString(R.string.internet_not_connected));
        }

        setIfPlaying();

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        int itemId = menuItem.getItemId();

        if (itemId == R.id.drawer_recent) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        else if (itemId == R.id.drawer_rate) {

            final String package_name = BuildConfig.APPLICATION_ID;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + package_name)));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + package_name)));
            }
            drawerLayout.closeDrawer(GravityCompat.START);

            return true;
        }

        else if (itemId == R.id.drawer_share) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_content) + "\n" + "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID);
            intent.setType("text/plain");
            startActivity(intent);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        else if (itemId == R.id.drawer_permission) {
            startActivity(new Intent(getApplicationContext(), ActivityPermission.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }

        else if (itemId == R.id.drawer_about) {
            aboutDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();

            return true;
        }


        else if (itemId == R.id.drawer_schedule) {
            Intent intent = new Intent(MainActivity.this, ScheduleActivity.class);
            startActivity(intent);
            return true;
        }

        else if (itemId == R.id.drawer_playlist) {
            Intent intent = new Intent(MainActivity.this, PlaylistActivity.class);
            startActivity(intent);
            return true;
        }

        //SOCIAL LINKS.........................................................

        else if (itemId == R.id.drawer_facebook) {
            loadWebsite(getString(R.string.facebook));
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }

//        else if (itemId == R.id.drawer_instagram) {
//            loadWebsite(getString(R.string.instagram));
//            drawerLayout.closeDrawer(GravityCompat.START);
//            hideKeyboard();
//            return true;
//        }
//
//        else if (itemId == R.id.drawer_twitter) {
//            loadWebsite(getString(R.string.twitter));
//            drawerLayout.closeDrawer(GravityCompat.START);
//            hideKeyboard();
//            return true;
//        }

        else if (itemId == R.id.drawer_youtube) {
            loadWebsite(getString(R.string.youtube));
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }

        else if (itemId == R.id.drawer_whatsapp) {
            loadWebsite("https://wa.me/" + getString(R.string.whatsapp));
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }

//        else if (itemId == R.id.drawer_tiktok) {
//            loadWebsite(getString(R.string.tiktok));
//            drawerLayout.closeDrawer(GravityCompat.START);
//            hideKeyboard();
//            return true;
//        }
//
//        else if (itemId == R.id.drawer_linkedIn) {
//            loadWebsite(getString(R.string.linkedin));
//            drawerLayout.closeDrawer(GravityCompat.START);
//            hideKeyboard();
//            return true;
//        }

        else if (itemId == R.id.drawer_website) {
            loadWebsite(getString(R.string.website));
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }

        else if (itemId == R.id.drawer_telephone) {
            PhoneIntents.from(this).showDialNumber(getString(R.string.phone_no)).show();
            drawerLayout.closeDrawer(GravityCompat.START);
            hideKeyboard();
            return true;
        }


        return false;
    }

    private void loadWebsite(String link){
        BrowserIntents.from(MainActivity.this).openLink( Uri.parse(link)).show();
    }

    public void setupNavigationDrawer(Toolbar toolbar) {
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
        };

        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
        toolbar.setTitle(R.string.app_name);
    }

    public void loadFrag(Fragment f1, FragmentManager fm) {
        for (int i = 0; i < fm.getBackStackEntryCount(); ++i) {
            fm.popBackStack();
        }
        FragmentTransaction ft = fm.beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.replace(R.id.fragment_container, f1);
        ft.commit();
    }

    public void changePlayPause(Boolean flag) {
        Constant.is_playing = flag;
        if (flag) {
            Radio radio = RadioPlayerService.getInstance().getPlayingRadioStation();
            if (radio != null) {
                changeText(radio);
                fabPlayExpand.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_button_pause));
                equalizerView.animateBars();
            }
        } else {
            if (Constant.item_radio.size() > 0) {
                changeText(Constant.item_radio.get(Constant.position));
            }
            fabPlayExpand.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_button_play));
            equalizerView.stopBars();
            imgAlbumArtLarge.setVisibility(View.GONE);
            //imgMusicBackground.setVisibility(View.GONE);
        }
    }

    public void showImageAlbumArt(boolean show) {
        if (show) {
            imgAlbumArtLarge.setVisibility(View.VISIBLE);
//            imgMusicBackground.setVisibility(View.VISIBLE);
        } else {
            imgAlbumArtLarge.setVisibility(View.GONE);
//            imgMusicBackground.setVisibility(View.GONE);
        }
    }

    public void changeText(Radio radio) {
        if (Constant.radio_type) {
            changeSongName(Constant.metadata);

            if (Constant.metadata == null || Constant.metadata.equals(radio.getRadio_genre())) {
                imgAlbumArtLarge.setVisibility(View.GONE);
//                imgMusicBackground.setVisibility(View.GONE);
            } else {
                imgAlbumArtLarge.setVisibility(View.VISIBLE);
//                imgMusicBackground.setVisibility(View.VISIBLE);
            }

            txtSongExpand.setVisibility(View.VISIBLE);
        } else {
            txtRadioMusicSong.setText("");
            txtSongExpand.setText(radio.getRadio_name());
            txtSongExpand.setVisibility(View.INVISIBLE);
        }
        txtRadioExpand.setText(radio.getRadio_name());

        if (!Constant.is_playing) {
            txtSongExpand.setText(radio.getRadio_genre());
        }

        Glide.with(getApplicationContext())
                .load(radio.getRadio_image_url().replace(" ", "%20"))
                .placeholder(R.drawable.radio_image)
                .transform(new BlurTransformation(Config.BG_IMAGE_BLUR_AMOUNT))
                .into(imgMusicBackground);

        Glide.with(getApplicationContext())
                .load(radio.getRadio_image_url().replace(" ", "%20"))
                .placeholder(R.drawable.radio_image)
                .into(imgRadioLarge);
    }

    public void changeSongName(String songName) {
        Constant.metadata = songName;
        txtSongExpand.setText(songName);
    }

    public void changeAlbumArt(String artworkUrl) {
        Constant.albumArt = artworkUrl;

        Glide.with(getApplicationContext())
                .load(artworkUrl.replace(" ", "%20"))
                .placeholder(android.R.color.transparent)
                .thumbnail(0.3f)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        imgAlbumArtLarge.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imgAlbumArtLarge.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(imgAlbumArtLarge);

        Glide.with(getApplicationContext())
                .load(artworkUrl.replace(" ", "%20"))
                .placeholder(android.R.color.transparent)
                .transform(new BlurTransformation(Config.BG_IMAGE_BLUR_AMOUNT))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        imgMusicBackground.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imgMusicBackground.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(imgMusicBackground);
    }

    public void setIfPlaying() {
        if (RadioPlayerService.getInstance() != null) {
            RadioPlayerService.initialize(MainActivity.this);
            changePlayPause(RadioPlayerService.getInstance().isPlaying());
        } else {
            changePlayPause(false);
        }
    }


    public void setBuffer(Boolean flag) {
        if (flag) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (count != 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            showExitDialog(true);
        }
    }

    public void showExitDialog(boolean exit) {
        if (exit) {
            if (lytExit.getVisibility() != View.VISIBLE) {
                lytExit.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up));
            }
            lytExit.setVisibility(View.VISIBLE);
        } else {
            lytExit.clearAnimation();
            lytExit.setVisibility(View.GONE);
        }
    }

    public void initExitDialog() {

        lytExit = findViewById(R.id.lyt_exit);
        lytDialog = findViewById(R.id.lyt_dialog);

        lytExit.setOnClickListener(v -> {
        });
        lytDialog.setOnClickListener(v -> {
        });

        findViewById(R.id.txt_cancel).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> showExitDialog(false), 200));
        findViewById(R.id.txt_minimize).setOnClickListener(v -> {
                    showExitDialog(false);
                    new Handler(Looper.getMainLooper()).postDelayed(this::minimizeApp, 200);
                }
        );
        findViewById(R.id.txt_exit).setOnClickListener(v -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finish();

            if (isServiceRunning()) {
                Intent stop = new Intent(MainActivity.this, RadioPlayerService.class);
                stop.setAction(RadioPlayerService.ACTION_STOP);
                startService(stop);
                Log.d(TAG, "Radio service is running");
            } else {
                Log.d(TAG, "Radio service is not running");
            }
        }, 200));

    }

    public void minimizeApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RadioPlayerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void hideKeyboard() {
        try {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            if (((getCurrentFocus() != null) && ((getCurrentFocus().getWindowToken() != null)))) {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void aboutDialog() {
        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        View view = layoutInflater.inflate(R.layout.custom_dialog_about, null);
        ((TextView) view.findViewById(R.id.txt_app_version)).setText(getString(R.string.sub_about_app_version) + " " + BuildConfig.VERSION_NAME);

        final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        alert.setView(view);
        alert.setCancelable(false);
        alert.setPositiveButton(R.string.option_ok, (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    public void openTimeSelectDialog() {
        AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
        alt_bld.setTitle(getString(R.string.sleep_time));

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.time_selection_dialog, null);
        alt_bld.setView(dialogView);

        final TextView tv_min = dialogView.findViewById(R.id.txt_minutes);
        tv_min.setText("1 " + getString(R.string.min));

        SeekBar seekBar = dialogView.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv_min.setText(progress + " " + getString(R.string.min));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        alt_bld.setPositiveButton(getString(R.string.set), (dialog, which) -> {
            String hours = String.valueOf(seekBar.getProgress() / 60);
            String minute = String.valueOf(seekBar.getProgress() % 60);

            if (hours.length() == 1) {
                hours = "0" + hours;
            }

            if (minute.length() == 1) {
                minute = "0" + minute;
            }

            String totalTime = hours + ":" + minute;
            long total_timer = utils.convertToMilliSeconds(totalTime) + System.currentTimeMillis();

            Random random = new Random();
            int id = random.nextInt(100);

            sharedPref.setSleepTime(true, total_timer, id);

            int FLAG;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FLAG = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT;
            } else {
                FLAG = PendingIntent.FLAG_ONE_SHOT;
            }

            Intent intent = new Intent(MainActivity.this, SleepTimeReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), id, intent, FLAG);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            assert alarmManager != null;
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, total_timer, pendingIntent);
        });
        alt_bld.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {

        });
        AlertDialog alert = alt_bld.create();
        alert.show();
    }

    public void openTimeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getString(R.string.sleep_time));
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.time_layout, null);
        builder.setView(dialogView);

        TextView textView = dialogView.findViewById(R.id.txt_time);

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {

        });

        builder.setPositiveButton(getString(R.string.stop), (dialog, which) -> {
            int FLAG;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FLAG = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT;
            } else {
                FLAG = PendingIntent.FLAG_ONE_SHOT;
            }
            Intent i = new Intent(MainActivity.this, SleepTimeReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, sharedPref.getSleepID(), i, FLAG);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            pendingIntent.cancel();
            assert alarmManager != null;
            alarmManager.cancel(pendingIntent);
            sharedPref.setSleepTime(false, 0, 0);
        });

        updateTimer(textView, sharedPref.getSleepTime());

        builder.show();
    }

    private void updateTimer(final TextView textView, long time) {
        long timeLeft = time - System.currentTimeMillis();
        if (timeLeft > 0) {
            @SuppressLint("DefaultLocale") String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(timeLeft),
                    TimeUnit.MILLISECONDS.toMinutes(timeLeft) % TimeUnit.HOURS.toMinutes(1),
                    TimeUnit.MILLISECONDS.toSeconds(timeLeft) % TimeUnit.MINUTES.toSeconds(1));

            textView.setText(hms);
            handler.postDelayed(() -> {
                if (sharedPref.getIsSleepTimeOn()) {
                    updateTimer(textView, sharedPref.getSleepTime());
                }
            }, 1000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        initComponent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



}
