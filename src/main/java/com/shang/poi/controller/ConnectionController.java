package com.shang.poi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.model.DatabaseConfig;
import com.shang.poi.pool.JdbcTemplatePool;
import com.shang.poi.service.ConnectionConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/24 18:48
 */
@Controller
@RequestMapping("/connection")
public class ConnectionController {

    @Resource
    private ConnectionConfigService connectionConfigService;

    @Resource(name = "jacksonObjectMapper")
    private ObjectMapper objectMapper;

    @Resource(name = "defaultValidator")
    private Validator validator;

    @PostConstruct
    public void init() {
        final List<ConnectionConfig> connectionConfigs = connectionConfigService.listAll();
        final ArrayList<Integer> closed = new ArrayList<>();
        for (final ConnectionConfig connectionConfig : connectionConfigs) {
            if (connectionConfig.getRunning() == 1) {
                try {
                    final DatabaseConfig databaseConfig = objectMapper.readValue(connectionConfig.getConfig(), DatabaseConfig.class);
                    JdbcTemplatePool.create(connectionConfig.getId(), databaseConfig);
                } catch (Exception e) {
                    closed.add(connectionConfig.getId());
                }
            }
        }
        connectionConfigService.updateStatusByIds(closed, 0);
        JdbcTemplatePool.check((id, closed1) -> {
            if (closed1) {
                final ConnectionConfig connectionConfig = new ConnectionConfig();
                connectionConfig.setId(id);
                connectionConfig.setRunning(0);
                connectionConfigService.update(connectionConfig);
            }
        });
    }

    @GetMapping("/config")
    @ResponseBody
    public List<ConnectionConfig> listAll() {
        return connectionConfigService.listAll();
    }

    @GetMapping("/connect/{id}")
    @ResponseBody
    public ConnectionConfig connect(@PathVariable Integer id) throws JsonProcessingException {
        final ConnectionConfig byId = connectionConfigService.getById(id);
        if (byId == null) {
            throw new RuntimeException("不存在的Id");
        }
        final DatabaseConfig databaseConfig = objectMapper.readValue(byId.getConfig(), DatabaseConfig.class);
        JdbcTemplatePool.create(byId.getId(), databaseConfig);
        byId.setRunning(1);
        final ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setId(byId.getId());
        connectionConfig.setRunning(byId.getRunning());
        connectionConfigService.update(connectionConfig);
        return byId;
    }

    @GetMapping("/disconnect/{id}")
    @ResponseBody
    public ConnectionConfig disconnect(@PathVariable Integer id) {
        final ConnectionConfig byId = connectionConfigService.getById(id);
        if (byId == null) {
            throw new RuntimeException("不存在的Id");
        }
        JdbcTemplatePool.close(byId.getId());
        byId.setRunning(0);
        final ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setId(byId.getId());
        connectionConfig.setRunning(byId.getRunning());
        connectionConfigService.update(connectionConfig);
        return byId;
    }

    @GetMapping("/{id}/check")
    @ResponseBody
    public List<Map<String, Object>> check(@PathVariable Integer id) {
        final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(id);
        return jdbcTemplate.queryForList("show databases ");
    }

}
