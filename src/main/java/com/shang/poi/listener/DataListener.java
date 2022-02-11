package com.shang.poi.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelDataConvertException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/10 14:18
 */
@Slf4j
public class DataListener extends AnalysisEventListener<Map<Integer, String>> {

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        log.info("head: {}", headMap);
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        log.info("data: {}", data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("end!!!");
    }

    /**
     * 在转换异常 获取其他异常下会调用本接口。抛出异常则停止读取。如果这里不抛出异常则 继续读取下一行。
     *
     * @param exception {@link Exception}
     * @param context   {@link AnalysisContext}
     * @throws Exception 抛出异常则停止读取
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        log.error("解析失败，但是继续解析下一行:{}", exception.getMessage());
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException excelDataConvertException = (ExcelDataConvertException) exception;
            log.error("第{}行，第{}列解析异常，数据为:{}", excelDataConvertException.getRowIndex(),
                    excelDataConvertException.getColumnIndex(), excelDataConvertException.getCellData());
        }
    }
}
