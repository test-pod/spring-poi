package com.shang.poi;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.github.pagehelper.PageInfo;
import com.shang.poi.config.PoiConfiguration;
import com.shang.poi.model.MockResultPlayBack;
import com.shang.poi.service.MockResultPlayBackService;
import com.shang.poi.vo.MockResultPlayBackVo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@EnableConfigurationProperties(PoiConfiguration.class)
public class SpringPoiApplication {

    private static final String SHEET_NAME = "Mock";

    public static void main(String[] args) {
        SpringApplication.run(SpringPoiApplication.class, args);
    }

    @Resource
    private MockResultPlayBackService mockResultPlayBackService;

    @Resource
    private PoiConfiguration poiConfiguration;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            final AtomicInteger pageNum = new AtomicInteger(1);
            final AtomicBoolean hasNextPage = new AtomicBoolean(true);
            final ExcelWriter writer = EasyExcel.write(Paths.get(poiConfiguration.getExportFile()).toFile(), MockResultPlayBackVo.class).build();
            final WriteSheet sheet = EasyExcel.writerSheet(SHEET_NAME).build();
            do {
                final PageInfo<MockResultPlayBack> pageInfo = mockResultPlayBackService.listByBatchNoAndPage(poiConfiguration.getBatchNo(), pageNum.getAndIncrement(), poiConfiguration.getPageSize());
                hasNextPage.set(pageInfo.isHasNextPage());
                final List<MockResultPlayBack> mockResultPlayBacks = pageInfo.getList();
                writer.write(mockResultPlayBacks, sheet);
            } while (hasNextPage.get());
            writer.finish();
            System.exit(0);
        };
    }

}
