package com.shang.poi.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/15 14:52
 */
@Controller
@Slf4j
public class JoinController {
    @GetMapping("/upload")
    public String get() {
        return "join";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestPart MultipartFile first, @RequestPart MultipartFile second) {
        log.info(first.getOriginalFilename());
        log.info(second.getOriginalFilename());
        return "ok";
    }
}
