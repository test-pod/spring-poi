package com.shang.poi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * Created by shangwei2009@hotmail.com on 2022/3/16 10:31
 */
@ConfigurationProperties(prefix = "file")
@Data
@Validated
public class FileProperties {
    /**
     * 存储导出文件的目录
     */
    @NotBlank
    private String xlsxPath;

    private Integer maxHistory = 10;
}
