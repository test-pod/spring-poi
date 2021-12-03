package com.shang.poi.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/28 18:16
 */
@Data
@AllArgsConstructor
public class Issue {

    public enum Type {
        DIFFERENT, MISSING, UNNECESSARY
    }

    private String field;
    private Type type;

}
