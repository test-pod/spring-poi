package com.shang.poi.common;

import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

/**
 * Created by shangwei2009@hotmail.com on 2021/9/1 19:41
 */
public interface BaseMapper<T> extends MySqlMapper<T>, Mapper<T> {
}
