package com.shang.poi.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shangwei2009@hotmail.com on 2022/3/18 10:01
 */
public class FileNameComparator implements Comparator<String> {

    // FORMATTER 跟 PATTERN是一对的
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss SSS", Locale.CHINA);
    public static final Pattern PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}-\\d{2} \\d{3}) \\d{3}.xlsx");

    @Override
    public int compare(String o1, String o2) {
        try {
            // 去除时间戳之外的部分
            final String time1 = timeStr(o1);
            final String time2 = timeStr(o2);
            final LocalDateTime dateTime1 = LocalDateTime.parse(time1, FORMATTER);
            final LocalDateTime dateTime2 = LocalDateTime.parse(time2, FORMATTER);
            return dateTime1.compareTo(dateTime2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String timeStr(String str) {
        final Matcher matcher = PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
}
