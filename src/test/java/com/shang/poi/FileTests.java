package com.shang.poi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shang.poi.common.FileNameComparator;
import com.shang.poi.model.Count;
import com.sun.nio.file.SensitivityWatchEventModifier;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by shangwei2009@hotmail.com on 2022/3/17 16:14
 */
@Slf4j
public class FileTests {
    @Test
    public void test01() throws IOException, InterruptedException {
        final String dir = "C:\\Users\\Shang\\IdeaProjects\\spring-poi\\spring-poi";
        final WatchService watchService = FileSystems.getDefault().newWatchService();
        Paths.get(dir).register(watchService, new WatchEvent.Kind[]{
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                },
                SensitivityWatchEventModifier.HIGH);
        while (true) {
            final WatchKey key = watchService.take();
            for (final WatchEvent<?> pollEvent : key.pollEvents()) {
                log.info("event: {}", pollEvent.context());
            }
            key.reset();
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void test02() {
        final String xlsx = String.format("%s %03d.%s", LocalDateTime.now().format(FileNameComparator.FORMATTER), RANDOM.nextInt(1000), "xlsx");
        final Pattern compile = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}-\\d{2} \\d{3}) \\d{3}.xlsx");
        final Matcher matcher = compile.matcher(xlsx);
        while (matcher.find()) {
            System.out.println(matcher.groupCount());
            for (int i = 0; i <= matcher.groupCount(); i++) {
                System.out.println(matcher.group(i));
            }
        }
    }

    @Test
    public void test03() throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 5, 3, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(3));
        /* 信号量 */
        final Semaphore semaphore = new Semaphore(0);
        final Future<?> future = pool.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                System.out.println(LocalDateTime.now());
            }
        });
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("cancel");
                // CompletableFuture无效
                future.cancel(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        final CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

    @Test
    public void test04() throws JsonProcessingException {
        final String content = Files.contentOf(new File("C:\\Users\\Shang\\桌面\\报文回放\\4.6~4.8.CUP.json"), StandardCharsets.UTF_8);
        final ObjectMapper objectMapper = new ObjectMapper();
        final HashMap<String, Count> map = objectMapper.readValue(content, new TypeReference<HashMap<String, Count>>() {
        });
        final Set<String> findKeys = map.values().stream().flatMap(count -> count.getFindKeys().stream()).collect(Collectors.toSet());
        System.out.println(findKeys.stream().collect(Collectors.joining("', '", "('", "')")));
    }
}
