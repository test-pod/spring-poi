package com.shang.poi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.shang.poi.config.PoiProperties;
import com.shang.poi.model.Count;
import com.shang.poi.model.Issue;
import com.shang.poi.model.MockResultPlayBack;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/14 10:04
 */
@Service
public class UPSCSummaryService {
    private static final ArrayBlockingQueue<List<?>> RESULT_QUEUE = new ArrayBlockingQueue<>(16);

    private static final Pattern PATTERN_1 = Pattern.compile("^\\[ERROR\\]: 请求的域(\\w+)=.*不一致$");

    private static final Pattern PATTERN_2 = Pattern.compile("^\\[ERROR\\]: 请求的域(\\w+)=.*多余$");

    private static final Pattern PATTERN_3 = Pattern.compile("^\\[ERROR\\]: 请求的域(\\w+)缺失$");

    private static final Pattern PATTERN = Pattern.compile("^[0-3]{64}|[0-3]{128}$");

    private static final String NEW_LINE = "\\r\\n|\\n|\\r";

    @Resource
    private MockResultPlayBackService mockResultPlayBackService;

    @Resource
    private PoiProperties poiProperties;

    @Resource(name = "jacksonObjectMapper")
    private ObjectMapper objectMapper;

    public void calc() throws InterruptedException {
        final CountDownLatch finish = new CountDownLatch(1);
        final AtomicBoolean hasNextPage = new AtomicBoolean(true);
        new Thread(() -> {
            try {
                final AtomicLong id = new AtomicLong(11423003L);
                do {
                    final PageInfo<MockResultPlayBack> pageInfo = mockResultPlayBackService.listByBatchNoAndId(poiProperties.getBatchNo(), id.get(), poiProperties.getPageSize());
                    final List<MockResultPlayBack> mockResultPlayBacks = pageInfo.getList();
                    RESULT_QUEUE.put(mockResultPlayBacks);
                    id.set(mockResultPlayBacks.isEmpty() ? Long.MAX_VALUE : mockResultPlayBacks.get(mockResultPlayBacks.size() - 1).getId());
                    hasNextPage.set(pageInfo.isHasNextPage()); // 必须放在put后面
                } while (hasNextPage.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                final HashMap<Issue, Count> map = new HashMap<>();
                while (hasNextPage.get() || !RESULT_QUEUE.isEmpty()) {
                    final List<?> result = RESULT_QUEUE.poll(5, TimeUnit.SECONDS);
                    if (result != null) {
                        for (final Object o : result) {
                            if (o instanceof MockResultPlayBack) {
                                final String compareFlag = ((MockResultPlayBack) o).getCompareFlag();
                                final Matcher matcher = PATTERN.matcher(compareFlag);
                                if (matcher.matches()) {
                                    for (int i = 0; i < compareFlag.length(); i++) {
                                        switch (compareFlag.charAt(i)) {
                                            case '1': {
                                                final Issue issue = new Issue(String.format("F%03d", i + 1), Issue.Type.DIFFERENT);
                                                updateMap(map, issue, ((MockResultPlayBack) o));
                                                break;
                                            }
                                            case '2': {
                                                final Issue issue = new Issue(String.format("F%03d", i + 1), Issue.Type.MISSING);
                                                updateMap(map, issue, ((MockResultPlayBack) o));
                                                break;
                                            }
                                            case '3': {
                                                final Issue issue = new Issue(String.format("F%03d", i + 1), Issue.Type.UNNECESSARY);
                                                updateMap(map, issue, ((MockResultPlayBack) o));
                                                break;
                                            }
                                            default:
                                                continue;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                System.out.println(objectMapper.writeValueAsString(map));
                finish.countDown();
            } catch (InterruptedException | JsonProcessingException e) {
                e.printStackTrace();
            }
        }).start();
        finish.await();
        System.exit(0);
    }

    private void updateMap(HashMap<Issue, Count> map, Issue issue, MockResultPlayBack mockResultPlayBack) {
        final String findKey = mockResultPlayBack.getFindKey();
        final String resultComment = mockResultPlayBack.getResultComment();
        map.computeIfPresent(issue, (k, v) -> {
            v.setCount(v.getCount() + 1);
            v.addFindKey(findKey);
            v.addResultComment(resultComment);
            return v;
        });
        map.computeIfAbsent(issue, k -> {
            final Count count = new Count();
            count.setCount(1);
            count.addFindKey(findKey);
            count.addResultComment(resultComment);
            return count;
        });
    }
}
