package com.shang.poi.controller;

import com.alibaba.excel.EasyExcel;
import com.shang.poi.listener.DataListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hpsf.ClassIDPredefined;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Objects;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/15 14:52
 */
@Controller
@Slf4j
public class JoinController {

    private static final MediaType EXCEL_14 = MediaType.parseMediaType(ClassIDPredefined.EXCEL_V14.getContentType());

    @GetMapping("/upload")
    public String get() {
        return "join";
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestPart @NotNull MultipartFile first, @RequestPart @NotNull MultipartFile second) throws IOException {
        final String firstFilename = getMultipartFilename(first);
        final String secondFilename = getMultipartFilename(second);
        EasyExcel.read(first.getInputStream(), new DataListener()).sheet().doRead();
        return "ok";
    }

    private String getMultipartFilename(MultipartFile multipartFile) {
        final String filename = StringUtils.cleanPath(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        if (filename.contains("..")) {
            throw new RuntimeException("不能使用..表示路径 " + filename);
        }
        if (multipartFile.isEmpty()) {
            throw new RuntimeException("文件内容为空 " + filename);
        }
        final MediaType mediaType = MediaType.parseMediaType(Objects.requireNonNull(multipartFile.getContentType()));
        if (!EXCEL_14.equalsTypeAndSubtype(mediaType)) {
            throw new RuntimeException(filename + "不是.xlsx");
        }
        return filename;
    }
}
