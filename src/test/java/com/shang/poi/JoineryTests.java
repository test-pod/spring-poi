package com.shang.poi;

import joinery.DataFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/10 16:00
 */
public class JoineryTests {
    @Test
    public void test01() {
        final DataFrame<Object> left = new DataFrame<>("a", "b");
        left.append("one", Arrays.asList(1, 2));
        left.append("two", Arrays.asList(3, 4));
        left.append("three", Arrays.asList(5, 6));
        DataFrame<Object> right = new DataFrame<>("c", "d");
        right.append("one", Arrays.asList(10, 20));
        right.append("two", Arrays.asList(30, 40));
        right.append("four", Arrays.asList(50, 60));
        left.join(right).index();
        System.out.println(left);
        System.out.println(right);
    }
}
