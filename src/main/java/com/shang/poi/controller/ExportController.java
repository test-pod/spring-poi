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
import java.util.stream.Collectors;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/6 13:44
 */
@Controller
@RequestMapping("/export")
@Validated
@Slf4j
public class ExportController {

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

    @PostMapping("/post")
    @ResponseBody
    public void post(HttpSession session, @RequestBody @Valid ExportSqlDTO exportSqlDTO) {
        synchronized (session) {
            final ConnectionConfig byId = connectionConfigService.getById(exportSqlDTO.getId());
            if (byId == null) {
                throw new RuntimeException("不存在的Id");
            }
            final AtomicBoolean running = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_RUNNING);
            if (running != null && running.get()) {
                // 不要这个判断可以让导出在同一个session内并行，但exit一旦为true就会让所有任务结束。
                throw new RuntimeException("任务正运行");
            }
            if (session.getAttribute(Constants.ATTR_NAME_PARAM) == null) {
                session.setAttribute(Constants.ATTR_NAME_PARAM, exportSqlDTO);
            } else {
                throw new RuntimeException("任务待开始");
            }
        }
    }

    @PostMapping("/stop")
    @ResponseBody
    public void stop(HttpSession session) {
        final AtomicBoolean exit = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_EXIT);
        if (exit != null) {
            exit.set(true);
        }
    }

    @GetMapping("/start")
    public SseEmitter sseEmitter(HttpSession session) {
        synchronized (session) {
            final SseEmitter sseEmitter = new SseEmitter(0L);
            final ExportSqlDTO exportSqlDTO = (ExportSqlDTO) session.getAttribute(Constants.ATTR_NAME_PARAM);
            if (exportSqlDTO == null) {
                try {
                    sseEmitter.send(new Msg("任务为空或任务已开始"));
                } catch (IOException e) {
                    log.error(e.getLocalizedMessage(), e);
                } finally {
                    sseEmitter.complete();
                }
            } else {
                session.removeAttribute(Constants.ATTR_NAME_PARAM);
                final AtomicBoolean aExit = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_EXIT);
                if (aExit == null) {
                    session.setAttribute(Constants.ATTR_NAME_EXIT, new AtomicBoolean(false));
                }
                final AtomicBoolean exit = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_EXIT);
                exit.set(false);
                final AtomicBoolean aRunning = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_RUNNING);
                if (aRunning == null) {
                    session.setAttribute(Constants.ATTR_NAME_RUNNING, new AtomicBoolean(true));
                }
                final AtomicBoolean running = (AtomicBoolean) session.getAttribute(Constants.ATTR_NAME_RUNNING);
                running.set(true);
                CompletableFuture.runAsync(() -> {
                    exportService.export(sseEmitter, exportSqlDTO, exit);
                }, POOL).whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable.getLocalizedMessage(), throwable);
                    }
                    sseEmitter.complete();
                    exit.set(true);
                    running.set(false);
                });
            }
            return sseEmitter;
        }
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

}
