package com.shang.poi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/7 16:24
 */
@Controller
public class MainController {

    @GetMapping(value = {"", "/", "/index.html", "/index"})
    public String main() {
        return "index";
    }
}
