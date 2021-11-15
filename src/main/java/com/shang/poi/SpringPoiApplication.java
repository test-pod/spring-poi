package com.shang.poi;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.github.pagehelper.PageInfo;
import com.shang.poi.model.MockResultPlayBack;
import com.shang.poi.service.MockResultPlayBackService;
import com.shang.poi.vo.MockResultPlayBackVo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class SpringPoiApplication {

    private static final String BATCH_NO = "20211112203136794";

    private static final Integer PAGE_SIZE = 1000;

    private static final ArrayBlockingQueue<MockResultPlayBack> CACHE = new ArrayBlockingQueue<>(10000);

    public static void main(String[] args) {
        SpringApplication.run(SpringPoiApplication.class, args);
    }

    @Resource
    private MockResultPlayBackService mockResultPlayBackService;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            final AtomicBoolean readComplete = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    final AtomicInteger pageNum = new AtomicInteger(1);
                    final AtomicBoolean hasNextPage = new AtomicBoolean(true);
                    do {
                        final PageInfo<MockResultPlayBack> pageInfo = mockResultPlayBackService.listByBatchNoAndPage(BATCH_NO, pageNum.getAndIncrement(), PAGE_SIZE);
                        hasNextPage.set(pageInfo.isHasNextPage());
                        final List<MockResultPlayBack> mockResultPlayBacks = pageInfo.getList();
                        for (final MockResultPlayBack mockResultPlayBack : mockResultPlayBacks) {
                            CACHE.put(mockResultPlayBack);
                        }
                    } while (hasNextPage.get());
                    readComplete.set(true);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            new Thread(() -> {
                try {
                    final ExcelWriter writer = EasyExcel.write(Paths.get("Mock.xlsx").toFile(), MockResultPlayBackVo.class).build();
                    final WriteSheet sheet = EasyExcel.writerSheet("Mock").build();
                    while (!readComplete.get()) {
                        final MockResultPlayBack mockResultPlayBack = CACHE.take();
                        writer.write(Collections.singleton(mockResultPlayBack), sheet);
                    }
                    if (CACHE.size() > 0) {
                        for (MockResultPlayBack mockResultPlayBack : CACHE) {
                            writer.write(Collections.singleton(mockResultPlayBack), sheet);
                        }
                    }
                    writer.finish();
                    latch.countDown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();

            latch.await();
            System.exit(0);
        };
    }

}
