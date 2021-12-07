package com.shang.poi.vo;

import com.shang.poi.model.ConnectionConfig;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/7 14:47
 */
@Data
public class ConnectionConfigVO {

    public ConnectionConfigVO() {

    }

    public ConnectionConfigVO(ConnectionConfig connectionConfig) {
        BeanUtils.copyProperties(connectionConfig, this);
    }

    private Integer id;

    private String name;

    private Integer running;
}
