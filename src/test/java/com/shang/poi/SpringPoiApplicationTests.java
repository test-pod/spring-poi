package com.shang.poi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@SpringBootTest
class SpringPoiApplicationTests {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        final List<Map<String, Object>> maps = jdbcTemplate.queryForList("select * from mock_result_play_back");
        System.out.println(maps);
        final List<Object> query = jdbcTemplate.query("select * from mock_result_play_back", BeanPropertyRowMapper.newInstance(Object.class));
        System.out.println(query);
    }

}
