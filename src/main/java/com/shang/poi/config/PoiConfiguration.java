package com.shang.poi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/16 19:13
 */
@ConfigurationProperties(prefix = "poi")
@Data
@Validated
public class PoiConfiguration {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss", Locale.CHINA);

    @NotBlank
    private String batchNo;
    @Pattern(regexp = ".+\\.xlsx$")
    private String exportFile = LocalDateTime.now().format(FORMATTER) + ".xlsx";
    @Positive
    private Integer pageSize = 10000;
}
