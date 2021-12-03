package com.shang.poi.service;

import com.shang.poi.dao.ConnectionConfigMapper;
import com.shang.poi.model.ConnectionConfig;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.List;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/24 18:54
 */
@Service
public class ConnectionConfigService {

    @Resource
    private ConnectionConfigMapper connectionConfigMapper;

    public List<ConnectionConfig> listAll() {
        return connectionConfigMapper.selectAll();
    }

    public ConnectionConfig getById(Integer id) {
        return connectionConfigMapper.selectByPrimaryKey(id);
    }

    public int updateStatusByIds(List<Integer> ids, Integer running) {
        if (ids.size() > 0) {
            final Example example = new Example(ConnectionConfig.class);
            example.createCriteria().andIn("id", ids);
            final ConnectionConfig record = new ConnectionConfig();
            record.setRunning(0);
            return connectionConfigMapper.updateByExampleSelective(record, example);
        } else {
            return 0;
        }
    }

    public int update(ConnectionConfig connectionConfig) {
        return connectionConfigMapper.updateByPrimaryKeySelective(connectionConfig);
    }
}

