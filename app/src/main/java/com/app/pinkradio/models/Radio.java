package com.app.pinkradio.models;

public class Radio {

    public long radio_id = -1;
    public String radio_name = "";
    public String radio_genre = "";
    public String radio_url = "";
    public String radio_image_url = "";

    public Radio() {
    }

    public long getRadio_id() {
        return radio_id;
    }

    public void setRadio_id(long radio_id) {
        this.radio_id = radio_id;
    }

    public String getRadio_name() {
        return radio_name;
    }

    public void setRadio_name(String radio_name) {
        this.radio_name = radio_name;
    }

    public String getRadio_genre() {
        return radio_genre;
    }

    public void setRadio_genre(String radio_genre) {
        this.radio_genre = radio_genre;
    }

    public String getRadio_url() {
        return radio_url;
    }

    public void setRadio_url(String radio_url) {
        this.radio_url = radio_url;
    }

    public String getRadio_image_url() {
        return radio_image_url;
    }

    public void setRadio_image_url(String radio_image_url) {
        this.radio_image_url = radio_image_url;
    }

}
