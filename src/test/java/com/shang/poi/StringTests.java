package com.shang.poi;

import com.shang.poi.model.Issue;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/28 18:04
 */
public class StringTests {

    private static final Pattern PATTERN_1 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)=.*不一致$");

    private static final Pattern PATTERN_2 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)=.*多余$");

    private static final Pattern PATTERN_3 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)缺失$");

    private static final Pattern PATTERN = Pattern.compile("^[0-3]{64}|[0-3]{128}$");

    @Test
    public void test01() {
        final Matcher matcher = PATTERN_1.matcher("[ERROR]: 请求的属性orderDesc=灿然烤肉店与预期值贵港市港北区灿然烤肉店不一致\n");
        if (matcher.find()) {
            System.out.println(matcher.group(1));
        }
    }

    @Test
    public void test02() {
        final Issue a1 = new Issue("a", Issue.Type.DIFFERENT);
        final Issue a2 = new Issue("a", Issue.Type.DIFFERENT);
        System.out.println(a1.equals(a2));
    }

    @Test
    public void test03() {
        final String s64 = "00000000000000000000000000000000000000000000000000000000000000001";
        final Matcher matcher = PATTERN.matcher(s64);
        System.out.println(matcher.matches());
        System.out.println(matcher.lookingAt());
    }
}
