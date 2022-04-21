package com.shang.poi.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.shang.poi.dao.MockResultPlayBackMapper;
import com.shang.poi.model.MockResultPlayBack;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/15 16:59
 */
@Service
public class MockResultPlayBackService {

    @Resource
    private MockResultPlayBackMapper mockResultPlayBackMapper;

    public PageInfo<MockResultPlayBack> listByBatchNoAndPage(String batchNo, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        final Example example = new Example(MockResultPlayBack.class);
        example.createCriteria().andEqualTo("batchNo", batchNo);
        final List<MockResultPlayBack> mockResultPlayBacks = mockResultPlayBackMapper.selectByExample(example);
//        final List<MockResultPlayBack> mockResultPlayBacks = mockResultPlayBackMapper.selectAll();
        return PageInfo.of(mockResultPlayBacks);
    }

    public PageInfo<MockResultPlayBack> listQByBatchNoAndId(String batchNo, Long id, Integer pageSize) {
        PageHelper.startPage(1, pageSize);
        final Example example = new Example(MockResultPlayBack.class);
        example.createCriteria()
//                .andEqualTo("batchNo", batchNo)
                .andEqualTo("msgOut1LineName", "NETPAY")
                .andGreaterThan("id", id)
                .andLessThan("id", 8588640L);
        example.orderBy("id").asc();
        return PageInfo.of(mockResultPlayBackMapper.selectByExample(example));
    }

    public PageInfo<MockResultPlayBack> listCByBatchNoAndId(String batchNo, Long id, Integer pageSize) {
        PageHelper.startPage(1, pageSize);
        final Example example = new Example(MockResultPlayBack.class);
        example.createCriteria()
//                .andEqualTo("batchNo", batchNo)
                .andIn("msgOut1LineName", Arrays.asList("CUPA", "CUPS"))
                .andGreaterThan("id", id)
                .andLessThan("id", 8588640L);
        example.orderBy("id").asc();
        return PageInfo.of(mockResultPlayBackMapper.selectByExample(example));
    }

}

