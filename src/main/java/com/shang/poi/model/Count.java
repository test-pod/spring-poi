package com.shang.poi.model;

import lombok.Data;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/14 10:58
 */
@Data
public class Count {
    private long count;

    private final ArrayBlockingQueue<String> findKeys = new ArrayBlockingQueue<>(4);
    private final ArrayBlockingQueue<String> resultComments = new ArrayBlockingQueue<>(4);

    public boolean addFindKey(String findKey) {
        return findKeys.offer(findKey);
    }

    public boolean addResultComment(String resultComment) {
        return resultComments.offer(resultComment);
    }
}
