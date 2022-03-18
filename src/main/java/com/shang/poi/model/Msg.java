package com.shang.poi.model;

import lombok.Data;

/**
 * Created by shangwei2009@hotmail.com on 2022/3/16 15:46
 */
@Data
public class Msg {
    private boolean running;
    private String text;

    public Msg() {
        this("");
    }

    public Msg(String text) {
        this(true, text);
    }

    public Msg(boolean running, String text) {
        this.running = running;
        this.text = text;
    }
}
