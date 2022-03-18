package com.shang.poi;

import com.shang.poi.config.FileProperties;
import com.shang.poi.config.PoiProperties;
import com.shang.poi.service.UPSCSummaryService;
import com.shang.poi.service.UPSQSummaryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

@SpringBootApplication
@EnableConfigurationProperties({PoiProperties.class, FileProperties.class})
public class SpringPoiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringPoiApplication.class, args);
    }

    @Resource
    private UPSQSummaryService upsqSummaryService;

    @Resource
    private UPSCSummaryService upscSummaryService;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
//            upscSummaryService.calc();
        };
    }

}
