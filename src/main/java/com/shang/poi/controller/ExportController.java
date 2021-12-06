package com.shang.poi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/6 13:44
 */
@Controller
@RequestMapping("/export")
@Validated
public class ExportController {

    @GetMapping
    public String get() {
        return "export";
    }
}
