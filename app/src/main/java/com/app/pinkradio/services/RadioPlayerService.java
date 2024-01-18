package com.app.pinkradio.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.app.pinkradio.BuildConfig;
import com.app.pinkradio.Config;
import com.app.pinkradio.R;
import com.app.pinkradio.activities.MainActivity;
import com.app.pinkradio.callbacks.CallbackAlbumArt;
import com.app.pinkradio.metadata.IcyHttpDataSourceFactory;
import com.app.pinkradio.models.AlbumArt;
import com.app.pinkradio.models.Radio;
import com.app.pinkradio.rests.RestAdapter;
import com.app.pinkradio.services.parser.URLParser;
import com.app.pinkradio.utils.Constant;
import com.app.pinkradio.utils.HttpsTrustManager;
import com.app.pinkradio.utils.Utils;
import com.vhall.android.exoplayer2.ExoPlaybackException;
import com.vhall.android.exoplayer2.ExoPlayerFactory;
import com.vhall.android.exoplayer2.PlaybackParameters;
import com.vhall.android.exoplayer2.Player;
import com.vhall.android.exoplayer2.Timeline;
import com.vhall.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.vhall.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.vhall.android.exoplayer2.source.ExtractorMediaSource;
import com.vhall.android.exoplayer2.source.MediaSource;
import com.vhall.android.exoplayer2.source.TrackGroupArray;
import com.vhall.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.vhall.android.exoplayer2.source.hls.HlsMediaSource;
import com.vhall.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.vhall.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.vhall.android.exoplayer2.trackselection.TrackSelectionArray;
import com.vhall.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.vhall.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


@SuppressLint("StaticFieldLeak")
@SuppressWarnings("deprecation")
public class RadioPlayerService extends Service implements AudioFocusChangedCallback {

    public static final String TAG = "MusicService";
    private MediaSessionCompat mediaSessionCompat;
    MediaControllerCompat mediaControllerCompat;
    private PlaybackStateCompat.Builder stateBuilder;
    private MediaSessionCompat.Callback callback;
    Call<CallbackAlbumArt> callbackCall = null;
    LoadSong loadSong;
    PlaybackStateCompat playbackState;
    NotificationCompat.Builder builder;
    static NotificationManager notificationManager;
    private BroadcastReceiver broadcastReceiver;

    Bitmap bitmap;
    private Boolean isCanceled = false;
    boolean isCounterRunning = false;
    static RadioPlayerService service;
    static Context context;
    static Radio radio;
    ComponentName componentName;
    AudioManager mAudioManager;
    PowerManager.WakeLock mWakeLock;
    Utils utils;
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID;
    public static final String ACTION_TOGGLE = BuildConfig.APPLICATION_ID + ".togglepause";
    public static final String ACTION_PLAY = BuildConfig.APPLICATION_ID + ".play";
    public static final String ACTION_NEXT = BuildConfig.APPLICATION_ID + ".next";
    public static final String ACTION_PREVIOUS = BuildConfig.APPLICATION_ID + ".prev";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".stop";

    public static final String MEDIA_SESSION_TAG = "MEDIA_SESSION";

    static public void initialize(Context context) {
        RadioPlayerService.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void initializeRadio(Context context, Radio station) {
        RadioPlayerService.context = context;
        radio = station;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static RadioPlayerService getInstance() {
        return service;
    }

    public static RadioPlayerService createInstance() {
        if (service == null) {
            service = new RadioPlayerService();
        }
        return service;
    }

    public Boolean isPlaying() {
        if (service == null) {
            return false;
        } else {
            if (Constant.exoPlayer != null) {
                return Constant.exoPlayer.getPlayWhenReady();
            } else {
                return false;
            }
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        utils = new Utils(context);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null) {
            mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }

        componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(componentName);

        LocalBroadcastManager.getInstance(this).registerReceiver(onCallIncome, new IntentFilter("android.intent.action.PHONE_STATE"));
        LocalBroadcastManager.getInstance(this).registerReceiver(onHeadPhoneDetect, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        Constant.exoPlayer = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), trackSelector);
        Constant.exoPlayer.addListener(eventListener);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        stateBuilder = new PlaybackStateCompat.Builder();

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleCommand(intent);
            }
        };

        MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                if (state.getState() == PlaybackStateCompat.STATE_PAUSED || state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    if (builder != null) {
                        notificationManager.notify(NOTIFICATION_ID, builder.build());
                    }
                }
                if (state.getState() == PlaybackStateCompat.STATE_PAUSED) {
                    stopForeground(false);
                } else if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    if (builder != null) {
                        startForeground(NOTIFICATION_ID, builder.build());
                    }
                }
            }

            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                if (builder != null) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_TOGGLE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_STOP);
        this.registerReceiver(broadcastReceiver, filter);

        callback = new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                mediaSessionCompat.setActive(true);
                newPlay();
            }

            @Override
            public void onPause() {
                togglePlayPause();
                if (Constant.exoPlayer.getPlayWhenReady()) {
                    mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PAUSED));
                } else {
                    mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PLAYING));
                }
            }

            @Override
            public void onSkipToNext() {
                next();
                mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT));
            }

            @Override
            public void onSkipToPrevious() {
                previous();
                mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS));
            }

            @Override
            public void onStop() {
                mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_STOPPED));
                mediaSessionCompat.setActive(false);
                stop(false);
            }

            @Override
            public void onSkipToQueueItem(long id) {
                mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM));
                onPlay();
            }

            @Override
            public void onSeekTo(long pos) {
                mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_BUFFERING));
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent mediaEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (mediaEvent.getAction() == KeyEvent.ACTION_UP) {
                    int keyCode = mediaEvent.getKeyCode();
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            onSkipToNext();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            onSkipToPrevious();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            onPause();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            if (isPlaying()) {
                                new Handler(Looper.getMainLooper()).postDelayed(() -> stop(false), 2000);
                                pause();
                            } else {
                                stop(false);
                            }
                            break;
                    }
                }
                return true;
            }
        };

        mediaSessionCompat = new MediaSessionCompat(this, MEDIA_SESSION_TAG);
        mediaSessionCompat.setCallback(callback);

        mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_NONE));
        mediaSessionCompat.setMetadata(new MediaMetadataCompat.Builder().build());

//        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
//                | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
//                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

//        mediaSessionCompat.setMetadata(new MediaMetadataCompat.Builder()
//                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
//                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
//                .build());

        mediaControllerCompat = new MediaControllerCompat(this, mediaSessionCompat);
        mediaControllerCompat.registerCallback(controllerCallback);

    }

    @Override
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        if (isPlaying()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ((MainActivity) context).finish();
                stop(false);
            }, 2000);
            pause();
        } else {
            ((MainActivity) context).finish();
            stop(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null)
            try {
                handleCommand(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        return START_NOT_STICKY;
    }

    private void handleCommand(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case ACTION_TOGGLE:
                callback.onPause();
                break;
            case ACTION_NEXT:
                callback.onSkipToNext();
                break;
            case ACTION_PREVIOUS:
                callback.onSkipToPrevious();
                break;
            case ACTION_PLAY:
                newPlay();
                break;
            case ACTION_STOP:
                if (isPlaying()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> callback.onStop(), 2000);
                    pause();
                } else {
                    callback.onStop();
                }
                break;
        }
    }

    private class LoadSong extends AsyncTask<String, Void, Boolean> {

        MediaSource mediaSource;

        protected void onPreExecute() {
            ((MainActivity) context).setBuffer(true);
            ((MainActivity) context).changeSongName(Constant.item_radio.get(Constant.position).radio_genre);
        }

        protected Boolean doInBackground(final String... args) {
            try {
                HttpsTrustManager.allowAllSSL();
                String url = Constant.item_radio.get(Constant.position).radio_url;
                DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), null, icy);
                if (url.contains(".m3u8") || url.contains(".M3U8")) {
                    mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                            .setAllowChunklessPreparation(false)
                            .setExtractorFactory(new DefaultHlsExtractorFactory(DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM))
                            .createMediaSource(Uri.parse(url));
                } else if (url.contains(".m3u") || url.contains("yp.shoutcast.com/sbin/tunein-station.m3u?id=")) {
                    url = URLParser.getUrl(url);
                    mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                            .setExtractorsFactory(new DefaultExtractorsFactory())
                            .createMediaSource(Uri.parse(url));
                } else if (url.contains(".pls") || url.contains("listen.pls?sid=") || url.contains("yp.shoutcast.com/sbin/tunein-station.pls?id=")) {
                    url = URLParser.getUrl(url);
                    mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                            .setExtractorsFactory(new DefaultExtractorsFactory())
                            .createMediaSource(Uri.parse(url));
                } else {
                    mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                            .setExtractorsFactory(new DefaultExtractorsFactory())
                            .createMediaSource(Uri.parse(url));
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (context != null) {
                super.onPostExecute(aBoolean);
                Constant.exoPlayer.seekTo(Constant.exoPlayer.getCurrentWindowIndex(), Constant.exoPlayer.getCurrentPosition());
                Constant.exoPlayer.prepare(mediaSource, false, false);
                Constant.exoPlayer.setPlayWhenReady(true);
                if (!aBoolean) {
                    ((MainActivity) context).setBuffer(false);
                    Toast.makeText(context, getString(R.string.error_loading_radio), Toast.LENGTH_SHORT).show();
                }
            }
        }

    }

    Player.EventListener eventListener = new Player.EventListener() {
        @Override
        public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {

        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                next();
            }
            if (playbackState == Player.STATE_READY && playWhenReady) {
                if (!isCanceled) {
                    //((MainActivity) context).seekBarUpdate();
                    ((MainActivity) context).setBuffer(false);
                    if (builder == null) {
                        createNotification();
                    } else {
                        notificationManager.notify(NOTIFICATION_ID, builder.build());
                        updateNotificationPlay(Constant.exoPlayer.getPlayWhenReady());
                    }

                    //Constant.radio_type = !Constant.item_radio.get(Constant.position).radio_type.equals("mp3");
                    updateNotificationAlbumArt(Constant.item_radio.get(Constant.position).radio_image_url);
                    updateNotificationMetadata(Constant.item_radio.get(Constant.position).radio_genre);

                    changePlayPause(true);

                    if (Config.ENABLE_RADIO_TIMEOUT) {
                        if (isCounterRunning) {
                            mCountDownTimer.cancel();
                        }
                    }

                } else {
                    isCanceled = false;
                    stopExoPlayer();
                }
            }
            if (playWhenReady) {
                if (!mWakeLock.isHeld()) {
                    mWakeLock.acquire(60000);
                }
            } else {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            stop(true);
            if (Config.ENABLE_RADIO_TIMEOUT) {
                if (isCounterRunning) {
                    mCountDownTimer.cancel();
                }
            }
        }

        @Override
        public void onPositionDiscontinuity(int reason) {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }

        @Override
        public void onSeekProcessed() {

        }
    };

    private void changePlayPause(Boolean play) {
        ((MainActivity) context).changePlayPause(play);
    }

    private void togglePlayPause() {
        if (Constant.exoPlayer.getPlayWhenReady()) {
            pause();
        } else {
            if (utils.isNetworkAvailable()) {
                play();
            } else {
                Toast.makeText(context, getString(R.string.internet_not_connected), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pause() {
        Constant.exoPlayer.setPlayWhenReady(false);
        changePlayPause(false);
        updateNotificationPlay(Constant.exoPlayer.getPlayWhenReady());
    }

    private void play() {
        Constant.exoPlayer.setPlayWhenReady(true);
        Constant.exoPlayer.seekTo(Constant.exoPlayer.getCurrentWindowIndex(), Constant.exoPlayer.getCurrentPosition());
        changePlayPause(true);
        updateNotificationPlay(Constant.exoPlayer.getPlayWhenReady());
        //((MainActivity) context).seekBarUpdate();
    }

    private void newPlay() {
        loadSong = new LoadSong();
        loadSong.execute();

        if (Config.ENABLE_RADIO_TIMEOUT) {
            if (isCounterRunning) {
                mCountDownTimer.cancel();
            }
            mCountDownTimer.start();
        }

    }

    CountDownTimer mCountDownTimer = new CountDownTimer(Config.RADIO_TIMEOUT_CONNECTION, 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
            isCounterRunning = true;
            Log.d(TAG, "seconds remaining: " + millisUntilFinished / 1000);
        }

        @Override
        public void onFinish() {
            isCounterRunning = false;
            stop(true);
        }
    };

    private void next() {
        if (Constant.item_radio != null && Constant.item_radio.size() > 0) {
            RadioPlayerService.createInstance().initializeRadio(context, Constant.item_radio.get(Constant.position));
            utils.getPosition(true);
            radio = Constant.item_radio.get(Constant.position);
            newPlay();
            //((MainActivity) context).hideSeekBar();
        }
    }

    private void previous() {
        if (Constant.item_radio != null && Constant.item_radio.size() > 0) {
            RadioPlayerService.createInstance().initializeRadio(context, Constant.item_radio.get(Constant.position));
            utils.getPosition(false);
            radio = Constant.item_radio.get(Constant.position);
            newPlay();
            //((MainActivity) context).hideSeekBar();
        }
    }

    private void stop(boolean showMessage) {
        if (Constant.exoPlayer != null) {
            try {
                mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
                LocalBroadcastManager.getInstance(this).unregisterReceiver(onCallIncome);
                LocalBroadcastManager.getInstance(this).unregisterReceiver(onHeadPhoneDetect);
                mAudioManager.unregisterMediaButtonEventReceiver(componentName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            changePlayPause(false);
            stopExoPlayer();
            service = null;
            stopForeground(true);
            stopSelf();
            ((MainActivity) context).setBuffer(false);
            ((MainActivity) context).changePlayPause(false);

            if (showMessage) {
                Toast.makeText(context, getString(R.string.error_loading_radio), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void stopExoPlayer() {
        if (Constant.exoPlayer != null) {
            Constant.exoPlayer.stop();
        }
    }

    private PlaybackStateCompat createPlaybackState(int state) {
        long playbackPos = 0;
        playbackState = stateBuilder.setState(state, playbackPos, 1.0f).build();
        return playbackState;
    }

    @Override
    public void onFocusGained() {
        mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PLAYING));
    }

    @Override
    public void onFocusLost() {
        mediaSessionCompat.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PAUSED));
    }

    public IcyHttpDataSourceFactory icy = new IcyHttpDataSourceFactory
            .Builder(Utils.getUserAgent())
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMillis(1000)
            .setIcyHeadersListener(icyHeaders -> {
            })
            .setIcyMetadataChangeListener(icyMetadata -> {
                try {
                    if (Config.SONG_METADATA) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if ("".equalsIgnoreCase(icyMetadata.getStreamTitle())) {
                                updateNotificationMetadata(Constant.item_radio.get(Constant.position).radio_genre);
                            } else {
                                updateNotificationMetadata(icyMetadata.getStreamTitle());
                                requestAlbumArt(icyMetadata.getStreamTitle());
                            }
                        }, 1000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).build();

    private void requestAlbumArt(String title) {
        if (Config.IMAGE_ALBUM_ART) {
            callbackCall = RestAdapter.createAlbumArtAPI().getAlbumArt(title, "music", 1);
            callbackCall.enqueue(new Callback<CallbackAlbumArt>() {
                public void onResponse(@NonNull Call<CallbackAlbumArt> call, @NonNull Response<CallbackAlbumArt> response) {
                    CallbackAlbumArt resp = response.body();
                    if (resp != null && resp.resultCount != 0) {
                        ArrayList<AlbumArt> albumArts = resp.results;
                        String artWorkUrl = albumArts.get(0).artworkUrl100.replace("100x100bb", "300x300bb");
                        ((MainActivity) context).changeAlbumArt(artWorkUrl);
                        updateNotificationAlbumArt(artWorkUrl);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> ((MainActivity) context).showImageAlbumArt(true), 100);
                        Log.d(TAG, "request album art success");
                    } else {
                        ((MainActivity) context).changeAlbumArt("");
                        updateNotificationAlbumArt("");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> ((MainActivity) context).showImageAlbumArt(false), 100);
                        Log.d(TAG, "request album art failed");
                    }
                }

                public void onFailure(@NonNull Call<CallbackAlbumArt> call, @NonNull Throwable th) {
                    Log.d(TAG, "onFailure");
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    public Radio getPlayingRadioStation() {
        return radio;
    }

    private void getBitmapFromURL(String src) {
        try {
            URL url = new URL(src.replace(" ", "%20"));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            bitmap = BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    BroadcastReceiver onCallIncome = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (isPlaying()) {
                if (state != null) {
                    if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK) || state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        Intent intent_stop = new Intent(context, RadioPlayerService.class);
                        intent_stop.setAction(ACTION_TOGGLE);
                        startService(intent_stop);
                        Toast.makeText(context, "there is an call!!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "whoops!!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    BroadcastReceiver onHeadPhoneDetect = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constant.is_playing) {
                togglePlayPause();
            }
        }
    };

    AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (Config.RESUME_RADIO_ON_PHONE_CALL) {
                    togglePlayPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) {
                    togglePlayPause();

                }
                break;
        }
    };

    @SuppressWarnings("UnusedReturnValue")
    private boolean createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
            return true;
        }
        return false;
    }

    private void createNotification() {
        createNotificationChannel();
        buildNotification();
    }

    @SuppressLint("StaticFieldLeak")
    private void updateNotificationAlbumArt(String artWorkUrl) {
        new AsyncTask<String, String, String>() {
            @Override
            protected String doInBackground(String... strings) {
                try {
                    getBitmapFromURL(artWorkUrl);
                    if (builder != null) {
                        builder.setLargeIcon(bitmap);
                        notificationManager.notify(NOTIFICATION_ID, builder.build());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
            }
        }.execute();
    }

    private void updateNotificationMetadata(String title) {
        if (builder != null) {
            ((MainActivity) context).changeSongName(title);
            builder.setContentTitle(Constant.item_radio.get(Constant.position).radio_name);
            builder.setContentText(title);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void buildNotification() {

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String title = Constant.item_radio.get(Constant.position).radio_name;
        String artist = Constant.item_radio.get(Constant.position).radio_genre;

        builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        builder.setContentIntent(pendingIntent)
                .setLargeIcon(Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.radio_image), 128, 128, false))
                .setTicker(title)
                .setSmallIcon(R.drawable.ic_radio_notif)
                .setContentTitle(title)
                .setContentText(artist)
                .setWhen(0)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        .setShowCancelButton(true)
                        .setShowActionsInCompactView(0, 1)
                )
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                //.addAction(R.drawable.ic_noti_previous, "previous", getPlaybackAction(ACTION_PREVIOUS))
                .addAction(R.drawable.ic_pause_white, "pause", getPlaybackAction(ACTION_TOGGLE))
                //.addAction(R.drawable.ic_noti_next, "next", getPlaybackAction(ACTION_NEXT))
                .addAction(R.drawable.ic_noti_close, "close", getPlaybackAction(ACTION_STOP));

        startForeground(NOTIFICATION_ID, builder.build());
    }

    @SuppressLint("RestrictedApi")
    private void updateNotificationPlay(Boolean isPlay) {
        if (builder != null) {
            builder.mActions.remove(0);
            Intent playIntent = new Intent(getApplicationContext(), RadioPlayerService.class);
            playIntent.setAction(ACTION_TOGGLE);
            if (isPlay) {
                builder.mActions.add(0, new NotificationCompat.Action(R.drawable.ic_pause_white, "pause", getPlaybackAction(ACTION_TOGGLE)));
            } else {
                builder.mActions.add(0, new NotificationCompat.Action(R.drawable.ic_play_arrow_white, "Play", getPlaybackAction(ACTION_TOGGLE)));
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private PendingIntent getPlaybackAction(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        try {
            mediaSessionCompat.release();
            unregisterReceiver(broadcastReceiver);

            Constant.exoPlayer.stop();
            Constant.exoPlayer.release();
            Constant.exoPlayer.removeListener(eventListener);
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            try {
                mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
                LocalBroadcastManager.getInstance(this).unregisterReceiver(onCallIncome);
                LocalBroadcastManager.getInstance(this).unregisterReceiver(onHeadPhoneDetect);
                mAudioManager.unregisterMediaButtonEventReceiver(componentName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

}
