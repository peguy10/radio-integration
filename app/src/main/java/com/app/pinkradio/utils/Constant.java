package com.app.pinkradio.utils;

import com.app.pinkradio.models.Radio;
import com.vhall.android.exoplayer2.SimpleExoPlayer;

import java.io.Serializable;
import java.util.ArrayList;

public class Constant implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int PERMISSIONS_REQUEST = 102;

    public static final int DELAY_PERFORM_CLICK = 1000;

    public static String metadata;
    public static String albumArt;
    public static SimpleExoPlayer exoPlayer;
    public static Boolean is_playing = false;
    public static Boolean radio_type = true;
    public static ArrayList<Radio> item_radio = new ArrayList<>();
    public static int position = 0;

}