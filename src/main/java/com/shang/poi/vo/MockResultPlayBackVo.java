package com.shang.poi.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/15 17:01
 * <p>
 * 报文回放mock结果表
 */
@Data
public class MockResultPlayBackVo {
    /**
     * 主键
     */
    @ExcelProperty("id")
    private Long id;

    @ExcelProperty("batch_no")
    private String batchNo;

    /**
     * diff库交易唯一标识
     */
    @ExcelProperty("diff_trans_key1")
    private String diffTransKey1;

    @ExcelProperty("mock_req_info")
    private String mockReqInfo;

    @ExcelProperty("mock_resp_info")
    private String mockRespInfo;

    /**
     * 测试结果
     */
    @ExcelProperty("`result`")
    private String result;

    /**
     * 测试结果详情
     */
    @ExcelProperty("result_comment")
    private String resultComment;

    /**
     * 64/128位4进制数，每位0：该域相同；2：该域缺失；3：该域多余；1：该域与diff库中不同
     */
    @ExcelProperty("compare_flag")
    private String compareFlag;

    @ExcelProperty("mock_req_time")
    private LocalDateTime mockReqTime;

    @ExcelProperty("mock_resp_time")
    private LocalDateTime mockRespTime;

    @ExcelProperty("pos_method")
    private String posMethod;

    /**
     * 存储8583中的msgType或网付中的msgType
     */
    @ExcelProperty("msg_type")
    private String msgType;

    @ExcelProperty("mrch_no")
    private String mrchNo;

    @ExcelProperty("trmnl_no")
    private String trmnlNo;

    @ExcelProperty("card_no")
    private String cardNo;

    /**
     * 卡基表示流水号，码基表示srcReseve
     */
    @ExcelProperty("flow_no")
    private String flowNo;

    /**
     * 支付中心对接的渠道：CUPA/CUPS
     */
    @ExcelProperty("msg_out1_line_name")
    private String msgOut1LineName;

    /**
     * 预留字段1
     */
    @ExcelIgnore
    private String extra1;
}
