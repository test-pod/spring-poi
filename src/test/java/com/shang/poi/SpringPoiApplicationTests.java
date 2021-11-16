package com.shang.poi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@SpringBootTest
class SpringPoiApplicationTests {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        final List<Map<String, Object>> maps = jdbcTemplate.queryForList("select * from mock_result_play_back");
        System.out.println(maps);
    }

}
