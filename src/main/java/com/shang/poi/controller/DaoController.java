package com.shang.poi.controller;

import com.shang.poi.dto.ExportSqlDTO;
import com.shang.poi.model.Blog;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.pool.JdbcTemplatePool;
import com.shang.poi.service.BlogService;
import com.shang.poi.service.ConnectionConfigService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.sql.DataSource;
import javax.validation.Valid;
import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/23 17:31
 */
@RestController
@Validated
public class DaoController {

    private static final long PAGE_SIZE = 10000;

    @Resource
    private BlogService blogService;

    private static final HashMap<Integer, JdbcTemplate> POOL = new HashMap<Integer, JdbcTemplate>() {{
        final HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306");
        dataSource.setUsername("dev");
        dataSource.setPassword("Dev@test");
        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
//        dataSource.setCatalog("ums_testin_uat");
        put(0, new JdbcTemplate(dataSource));
    }};

    @GetMapping("/blogs")
    public List<Blog> listAll() {
        return blogService.listAll();
    }

    @GetMapping("/test")
    public List<?> test() throws SQLException, IOException {
        final JdbcTemplate jdbcTemplate = POOL.get(0);
        final List<Map<String, Object>> maps = jdbcTemplate.queryForList("select * from ums_testin_uat.mock_result_play_back limit 100");
        final DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource instanceof Closeable) {
            ((Closeable) dataSource).close();
        } else {
            if (dataSource != null) {
                dataSource.getConnection().close();
            }
        }
        return maps;
    }

    @Resource
    private ConnectionConfigService connectionConfigService;

    private static final Boolean FAST_PAGE = false;

    private static final SqlParser.Config CONFIG = SqlParser.config().withLex(Lex.MYSQL);

    private static final SqlNode COUNT_ONE;

    static {
        try {
            COUNT_ONE = SqlParser.create("count(1)", CONFIG).parseExpression();
        } catch (SqlParseException e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/export")
    public Object export(@RequestBody @Valid ExportSqlDTO exportSqlDTO) {
        final ConnectionConfig byId = connectionConfigService.getById(exportSqlDTO.getId());
        if (byId == null) {
            throw new RuntimeException("不存在的Id");
        }
        final String rawSql = StringUtils.trimTrailingCharacter(StringUtils.trimTrailingWhitespace(exportSqlDTO.getSql()), ';');

        final SqlParser sqlParser = SqlParser.create(rawSql, CONFIG);
        try {
            final SqlNode sqlNode = sqlParser.parseStmt();
            switch (sqlNode.getKind()) {
                // 只有select、where
                case SELECT: {
                    final SqlSelect sqlSelect = (SqlSelect) sqlNode;
                    final SqlNodeList selectList = sqlSelect.getSelectList();
                    sqlSelect.setSelectList(SqlNodeList.of(COUNT_ONE));
                    final String sql = sqlSelect.toSqlString(MysqlSqlDialect.DEFAULT).toString();
                    // 分页查
                    final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(byId.getId());
                    final Map<String, Object> countMap = jdbcTemplate.queryForMap(sql);
                    final Long size = (Long) countMap.get(COUNT_ONE.toSqlString(MysqlSqlDialect.DEFAULT).toString());
                    sqlSelect.setSelectList(selectList);
                    new SqlOrderBy(SqlParserPos.ZERO, sqlSelect, SqlNodeList.EMPTY, SqlParser.create("0", CONFIG).parseExpression(), SqlParser.create(String.valueOf(PAGE_SIZE), CONFIG).parseExpression());
                    break;
                }
                // 包含order by、limit
                case ORDER_BY: {
                    final SqlOrderBy sqlOrderBy = (SqlOrderBy) sqlNode;
                    // limit的第二个参数不为空，则一定包含limit
                    if (sqlOrderBy.fetch != null) {
                        // 包含limit
                        final SqlLiteral sqlLiteral = (SqlLiteral) sqlOrderBy.fetch;
                        if (sqlLiteral.longValue(false) > PAGE_SIZE) {
                            throw new RuntimeException("页大小不能超过" + PAGE_SIZE);
                        }
                        // 不分页直接查
                    } else {
                        // 不包含limit
                        final SqlSelect sqlSelect = (SqlSelect) sqlOrderBy.query;
                        // 分页查

                    }
                    break;
                }
                default:
                    throw new RuntimeException("暂时只能处理Select");
            }

        } catch (SqlParseException e) {
            throw new RuntimeException("本系统不能处理该SQL语句", e);
        }

        final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(byId.getId());
        final Map<String, Object> countMap = jdbcTemplate.queryForMap("select count(1) as `count` from (" + rawSql + ") as temp");
        final Long size = (Long) countMap.get("count");
        if (size <= PAGE_SIZE) {
            return jdbcTemplate.queryForList(rawSql);
        } else {
            final long totalPage = totalPage(size);
            if (!FAST_PAGE) {
                for (int i = 0; i < totalPage; i++) {
                    final List<Map<String, Object>> list = jdbcTemplate.queryForList(String.format("%s limit %d, %d", rawSql, i * PAGE_SIZE, PAGE_SIZE));
                }
            } else {
                final SqlParser.Config config = SqlParser.config().withLex(Lex.MYSQL);
                final SqlParser parser = SqlParser.create(rawSql, config);
            }
            return size;
        }
    }

    private Long totalPage(Long size) {
        final long page = size / PAGE_SIZE;
        if (page * PAGE_SIZE < size) {
            return page + 1;
        } else {
            return page;
        }
    }
}
