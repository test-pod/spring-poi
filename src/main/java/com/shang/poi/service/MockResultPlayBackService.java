package com.shang.poi.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.shang.poi.dao.MockResultPlayBackMapper;
import com.shang.poi.model.MockResultPlayBack;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
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

}

