package com.shang.poi.pool;

import com.shang.poi.model.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/25 20:39
 */
public class JdbcTemplatePool {
    private static final ConcurrentHashMap<Integer, JdbcTemplate> POOL = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService CHECK_SERVICE = Executors.newSingleThreadScheduledExecutor();

    public static synchronized void create(Integer id, DatabaseConfig databaseConfig) {
        final JdbcTemplate template = POOL.get(id);
        if (template != null) {
            final HikariDataSource dataSource = (HikariDataSource) template.getDataSource();
            if (dataSource != null && !dataSource.isClosed()) {
                throw new RuntimeException("请先释放已有连接");
            }
        }
        try {
            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(databaseConfig.getUrl());
            hikariConfig.setUsername(databaseConfig.getUsername());
            hikariConfig.setPassword(databaseConfig.getPassword());
            final JdbcTemplate jdbcTemplate = new JdbcTemplate(new HikariDataSource(hikariConfig));
            POOL.put(id, jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException("新建连接失败");
        }
    }

    public static JdbcTemplate get(Integer id) {
        final JdbcTemplate template = POOL.get(id);
        if (template == null) {
            throw new RuntimeException("请先建立连接");
        } else {
            final HikariDataSource dataSource = (HikariDataSource) template.getDataSource();
            if (dataSource == null || dataSource.isClosed()) {
                throw new RuntimeException("请先建立连接");
            }
        }
        return template;
    }

    public static synchronized void close(Integer id) {
        final JdbcTemplate template = POOL.remove(id);
        if (template != null) {
            final HikariDataSource dataSource = (HikariDataSource) template.getDataSource();
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    public static synchronized void destroy() {
        POOL.forEach((k, v) -> {
            try {
                final HikariDataSource dataSource = (HikariDataSource) v.getDataSource();
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        POOL.clear();
    }

    public static boolean isOnline(Integer id) {
        final JdbcTemplate template = POOL.get(id);
        if (template == null) {
            return false;
        } else {
            final HikariDataSource dataSource = (HikariDataSource) template.getDataSource();
            return dataSource != null && !dataSource.isClosed();
        }
    }

    public static void check(JdbcTemplateListener listener) {
        if (listener != null) {
            CHECK_SERVICE.scheduleWithFixedDelay(() -> {
                for (final Integer id : POOL.keySet()) {
                    listener.accept(id, !isOnline(id));
                }
            }, 0L, 5L, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    public interface JdbcTemplateListener {
        void accept(Integer id, Boolean closed);
    }
}
