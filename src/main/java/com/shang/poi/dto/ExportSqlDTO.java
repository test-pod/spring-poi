package com.shang.poi.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/1 10:24
 */
@Data
public class ExportSqlDTO {
    @NotNull
    private Integer id;

    @NotBlank
    private String sql;

    /**
     * 偏移的字段，一般为有序字段，用来快速分页，为空时使用普通分页
     */
    private String offsetColumn;
}
