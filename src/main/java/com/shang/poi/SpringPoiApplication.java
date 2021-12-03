package com.shang.poi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.shang.poi.config.PoiConfiguration;
import com.shang.poi.model.Issue;
import com.shang.poi.model.MockResultPlayBack;
import com.shang.poi.service.MockResultPlayBackService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
@EnableConfigurationProperties(PoiConfiguration.class)
public class SpringPoiApplication {

    private static final String SHEET_NAME = "Mock";

    private static final ArrayBlockingQueue<List<?>> RESULT_QUEUE = new ArrayBlockingQueue<>(16);

    private static final Pattern PATTERN_1 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)=.*不一致$");

    private static final Pattern PATTERN_2 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)=.*多余$");

    private static final Pattern PATTERN_3 = Pattern.compile("^\\[ERROR\\]: 请求的属性(\\w+)缺失$");

    private static final String NEW_LINE = "\\r\\n|\\n|\\r";

    public static void main(String[] args) {
        SpringApplication.run(SpringPoiApplication.class, args);
    }

    @Resource
    private MockResultPlayBackService mockResultPlayBackService;

    @Resource
    private PoiConfiguration poiConfiguration;

    @Resource(name = "jacksonObjectMapper")
    private ObjectMapper objectMapper;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            final CountDownLatch finish = new CountDownLatch(1);
            final AtomicBoolean hasNextPage = new AtomicBoolean(true);
            new Thread(() -> {
                try {
                    final AtomicInteger pageNum = new AtomicInteger(1);
                    do {
                        final PageInfo<MockResultPlayBack> pageInfo = mockResultPlayBackService.listByBatchNoAndPage(poiConfiguration.getBatchNo(), pageNum.getAndIncrement(), poiConfiguration.getPageSize());
                        final List<MockResultPlayBack> mockResultPlayBacks = pageInfo.getList();
                        RESULT_QUEUE.put(mockResultPlayBacks);
                        hasNextPage.set(pageInfo.isHasNextPage()); // 必须放在put后面
                    } while (hasNextPage.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            new Thread(() -> {
                try {
//                    final ExcelWriter writer = EasyExcel.write(Paths.get(poiConfiguration.getExportFile()).toFile(), MockResultPlayBackVo.class).build();
//                    final WriteSheet sheet = EasyExcel.writerSheet(SHEET_NAME).build();
                    final HashMap<Issue, Long> map = new HashMap<>();
                    while (hasNextPage.get() || !RESULT_QUEUE.isEmpty()) {
                        final List<?> result = RESULT_QUEUE.poll(5, TimeUnit.SECONDS);
                        if (result != null) {
                            for (final Object o : result) {
                                if (o instanceof MockResultPlayBack) {
                                    final String resultComment = ((MockResultPlayBack) o).getResultComment();
                                    final String[] split = resultComment.split(NEW_LINE);
                                    for (final String s : split) {
                                        final Matcher matcher1 = PATTERN_1.matcher(s);
                                        final Matcher matcher2 = PATTERN_2.matcher(s);
                                        final Matcher matcher3 = PATTERN_3.matcher(s);
                                        if (matcher1.find()) {
                                            final String group = matcher1.group(1);
                                            final Issue issue = new Issue(group, Issue.Type.DIFFERENT);
                                            map.computeIfPresent(issue, (key, value) -> value + 1L);
                                            map.computeIfAbsent(issue, key -> 1L);
                                        } else if (matcher2.find()) {
                                            final String group = matcher2.group(1);
                                            final Issue issue = new Issue(group, Issue.Type.UNNECESSARY);
                                            map.computeIfPresent(issue, (key, value) -> value + 1L);
                                            map.computeIfAbsent(issue, key -> 1L);
                                        } else if (matcher3.find()) {
                                            final String group = matcher3.group(1);
                                            final Issue issue = new Issue(group, Issue.Type.MISSING);
                                            map.computeIfPresent(issue, (key, value) -> value + 1L);
                                            map.computeIfAbsent(issue, key -> 1L);
                                        }
                                    }
                                }
                            }
//                            writer.write(result, sheet);
                        }
                    }
//                    writer.finish();
                    System.out.println(objectMapper.writeValueAsString(map));
                    finish.countDown();
                } catch (InterruptedException | JsonProcessingException e) {
                    e.printStackTrace();
                }
            }).start();
            finish.await();
            System.exit(0);
        };
    }

}
