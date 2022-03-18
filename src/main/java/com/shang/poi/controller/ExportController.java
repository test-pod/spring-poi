package com.shang.poi.controller;

import com.shang.poi.common.Constants;
import com.shang.poi.dto.ExportSqlDTO;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.model.Msg;
import com.shang.poi.service.ConnectionConfigService;
import com.shang.poi.service.ExportService;
import com.shang.poi.service.StorageService;
import com.shang.poi.vo.ConnectionConfigVO;
import com.shang.poi.vo.ExportFileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/6 13:44
 */
@Controller
@RequestMapping("/export")
@Validated
@Slf4j
public class ExportController {

    private static final ReentrantLock LOCK = new ReentrantLock();

    // 总共10个线程（导出）
    private static final int EXPORT_THREAD = 10;

    /* 负责执行客户端代码的线程池，根据《Java 开发手册》不可用 Executor 创建，有 OOM 的可能 */
    private static final ExecutorService POOL = new ThreadPoolExecutor(EXPORT_THREAD, EXPORT_THREAD,
            0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(EXPORT_THREAD));
    // 以上队列最大任务容纳数量是WORKER_THREAD，当所有线程繁忙时，新的任务会触发异常

    @Resource
    private ConnectionConfigService connectionConfigService;

    @Resource
    private ExportService exportService;

    @Resource
    private StorageService storageService;

    @GetMapping
    public String export(Map<String, Object> map) {
        final List<ConnectionConfigVO> connectionConfigVOS = connectionConfigService.listOnline();
        map.put("connections", connectionConfigVOS);
        updateFiles(map);
        return "export";
    }

    @GetMapping("/fragment")
    public String fragment(Map<String, Object> map) {
        updateFiles(map);
        return "export::file-list";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String filename) {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + storageService.loadAsResource(filename).getFilename() + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(storageService.loadAsResource(filename));
    }

    @PostMapping("/start")
    @ResponseBody
    public void start(HttpSession session, @RequestBody @Valid ExportSqlDTO exportSqlDTO) {
        final AtomicBoolean running = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_RUNNING);
        if (running == null || !running.get()) {
            final ConnectionConfig byId = connectionConfigService.getById(exportSqlDTO.getId());
            if (byId == null) {
                throw new RuntimeException("不存在的Id");
            }
            CompletableFuture.runAsync(() -> exportService.export(session, exportSqlDTO, byId), POOL);
        }
    }

    @PostMapping("/stop")
    @ResponseBody
    public void stop(HttpSession session) {
        final AtomicBoolean exit = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_EXIT);
        if (exit != null) {
            exit.set(true);
            session.removeAttribute(Constants.ATTR_NAME_EXIT);
        }
    }

    @GetMapping("/sse")
    @SuppressWarnings("unchecked")
    public SseEmitter sseEmitter(HttpSession session) {
        final SseEmitter sseEmitter = new SseEmitter(0L);
        sseEmitter.onCompletion(() -> {
            session.removeAttribute(Constants.ATTR_NAME_ONE_CLIENT);
        });
        sseEmitter.onError(throwable -> {
            session.removeAttribute(Constants.ATTR_NAME_ONE_CLIENT);
            log.error(throwable.getLocalizedMessage(), throwable);
            sseEmitter.completeWithError(throwable);
        });
        final ArrayBlockingQueue<Msg> logging = (ArrayBlockingQueue<Msg>) session.getAttribute(Constants.ATTR_NAME_LOG);
        final AtomicBoolean running = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_RUNNING);
        withLock(() -> {
            final Object oneClient = session.getAttribute(Constants.ATTR_NAME_ONE_CLIENT);
            if (oneClient == null) {
                session.setAttribute(Constants.ATTR_NAME_ONE_CLIENT, Constants.ATTR_NAME_ONE_CLIENT);
                CompletableFuture.runAsync(() -> {
                    try {
                        if (running != null && logging != null) {
                            while (running.get() || !logging.isEmpty()) {
                                final Msg msg = logging.poll(5, TimeUnit.SECONDS);
                                if (msg != null) {
                                    sseEmitter.send(msg);
                                }
                            }
                        }
                    } catch (IOException | InterruptedException ignored) {
                    } finally {
                        sseEmitter.complete();
                        session.removeAttribute(Constants.ATTR_NAME_ONE_CLIENT);
                    }
                });
            } else {
                sseEmitter.complete();
                session.removeAttribute(Constants.ATTR_NAME_ONE_CLIENT);
            }
        });
        return sseEmitter;
    }

    private void updateFiles(Map<String, Object> map) {
        map.put("files", storageService.loadAll().map(path -> {
                    final ExportFileVO exportFileVO = new ExportFileVO();
                    final String urlPath = MvcUriComponentsBuilder.fromMethodName(ExportController.class,
                            "serveFile", path.getFileName().toString()).build().toUri().toString();
                    exportFileVO.setName(path.getFileName().toString());
                    exportFileVO.setUrl(urlPath);
                    return exportFileVO;
                })
                .collect(Collectors.toList()));
    }

    private void withLock(Runnable runnable) {
        LOCK.lock();
        try {
            runnable.run();
        } finally {
            LOCK.unlock();
        }
    }

}
