package com.shang.poi.model;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/25 20:14
 */
@Data
public class DatabaseConfig {

    /**
     * URL_REGEX
     */
    private static final String URL_REGEX = "^jdbc:(mysql|derby)://\\b((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\.((?!\\d\\d\\d)\\d+|1\\d\\d|2[0-4]\\d|25[0-5])\\b:[1-9]\\d{1,4}(/\\S+)*$";

    @Pattern(regexp = URL_REGEX)
    private String url;

    @NotEmpty
    private String username;

    @NotEmpty
    private String password;
}
