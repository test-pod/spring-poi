package com.shang.poi.model;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.Data;

/**
  * Created by shangwei2009@hotmail.com on 2021/11/23 10:43
  */

/**
 * 报文回放mock结果表
 */
@Data
@Table(name = "mock_result_play_back")
public class MockResultPlayBack {
    /**
     * 主键
     */
    @Id
    @Column(name = "id")
    @GeneratedValue(generator = "JDBC")
    private Long id;

    @Column(name = "batch_no")
    private String batchNo;

    /**
     * diff库交易唯一标识
     */
    @Column(name = "diff_trans_key1")
    private String diffTransKey1;

    @Column(name = "find_key")
    private String findKey;

    @Column(name = "mock_req_info")
    private String mockReqInfo;

    @Column(name = "mock_resp_info")
    private String mockRespInfo;

    /**
     * 测试结果
     */
    @Column(name = "`result`")
    private String result;

    /**
     * 测试结果详情
     */
    @Column(name = "result_comment")
    private String resultComment;

    /**
     * 64/128位4进制数，每位0：该域相同；2：该域缺失；3：该域多余；1：该域与diff库中不同
     */
    @Column(name = "compare_flag")
    private String compareFlag;

    @Column(name = "mock_req_time")
    private LocalDateTime mockReqTime;

    @Column(name = "mock_resp_time")
    private LocalDateTime mockRespTime;

    @Column(name = "pos_method")
    private String posMethod;

    /**
     * 存储8583中的msgType或网付中的msgType
     */
    @Column(name = "msg_type")
    private String msgType;

    @Column(name = "mrch_no")
    private String mrchNo;

    @Column(name = "trmnl_no")
    private String trmnlNo;

    @Column(name = "card_no")
    private String cardNo;

    /**
     * 卡基表示流水号，码基表示srcReseve
     */
    @Column(name = "flow_no")
    private String flowNo;

    /**
     * 支付中心对接的渠道：CUPA/CUPS
     */
    @Column(name = "msg_out1_line_name")
    private String msgOut1LineName;
}