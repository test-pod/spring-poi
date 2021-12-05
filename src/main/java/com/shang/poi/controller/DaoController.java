package com.shang.poi.controller;

import com.shang.poi.dto.ExportSqlDTO;
import com.shang.poi.model.Blog;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.pool.JdbcTemplatePool;
import com.shang.poi.service.BlogService;
import com.shang.poi.service.ConnectionConfigService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.cnfexpression.MultiAndExpression;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/23 17:31
 */
@RestController
@Validated
@Slf4j
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

    public static final String COUNT_ONE = "count(1)";

    private static final List<SelectItem> COUNT_ONE_ITEMS;

    static {
        try {
            COUNT_ONE_ITEMS = Collections.singletonList(new SelectExpressionItem(CCJSqlParserUtil.parseExpression(COUNT_ONE)));
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/export")
    public Object export(@RequestBody @Valid ExportSqlDTO exportSqlDTO) throws JSQLParserException {
        final ConnectionConfig byId = connectionConfigService.getById(exportSqlDTO.getId());
        if (byId == null) {
            throw new RuntimeException("不存在的Id");
        }
        final String rawSql = StringUtils.trimTrailingCharacter(StringUtils.trimTrailingWhitespace(exportSqlDTO.getSql()), ';');
        final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(byId.getId());

        final Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(rawSql);
            if (!(statement instanceof Select)) {
                throw new RuntimeException("暂时只能处理select");
            }
        } catch (JSQLParserException e) {
            throw new RuntimeException("本系统不能处理该SQL语句", e);
        }
        final Select select = (Select) statement;

        final PlainSelect plainSelect = select.getSelectBody(PlainSelect.class);
        final Limit limit = plainSelect.getLimit();
        // 包含limit
        if (limit != null) {
            final LongValue rowCount = limit.getRowCount(LongValue.class);
            if (rowCount.getValue() > PAGE_SIZE) {
                throw new RuntimeException("页大小不能超过" + PAGE_SIZE);
            }
            // 不分页，直接查
            return jdbcTemplate.queryForList(rawSql);
        }
        // 不包含limit
        else {
            final List<SelectItem> selectItems = plainSelect.getSelectItems();
            // 修改
            plainSelect.setSelectItems(COUNT_ONE_ITEMS);

            final Map<String, Object> countMap = jdbcTemplate.queryForMap(select.toString());
            // 恢复
            plainSelect.setSelectItems(selectItems);
            final Long size = (Long) countMap.get(COUNT_ONE);
            final long totalPage = totalPage(size);
            // 分页查
            // 普通分页
            if (!StringUtils.hasText(exportSqlDTO.getOffsetColumn())) {
                for (int i = 0; i < totalPage; i++) {
                    final List<Map<String, Object>> list = jdbcTemplate.queryForList(String.format("%s limit %d, %d", rawSql, i * PAGE_SIZE, PAGE_SIZE));
                }
            }
            // 快速分页（必须存在关键字参数）
            else {
                final List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
                // 存在order by吗
                final Optional<OrderByElement> orderByColumnOptional = Optional.ofNullable(orderByElements)
                        .flatMap(list -> list.stream().filter(orderByElement -> {
                            final Expression expression = orderByElement.getExpression();
                            if (expression instanceof Column) {
                                return Objects.equals(exportSqlDTO.getOffsetColumn(), ((Column) expression).getColumnName());
                            }
                            return false;
                        }).findAny());
                // 关键字参数已存在（说明一定存在order by）
                if (orderByColumnOptional.isPresent()) {
                    // 修改
                    orderByColumnOptional.get().setAsc(true);
                }
                // 关键字参数未存在 或 不存在order by
                else {
                    try {
                        // 修改
                        plainSelect.addOrderByElements(new OrderByElement().withExpression(CCJSqlParserUtil.parseExpression(exportSqlDTO.getOffsetColumn())).withAsc(true));
                    } catch (JSQLParserException e) {
                        log.error(e.getLocalizedMessage(), e);
                    }
                }

                final Expression where = plainSelect.getWhere();
                // 原本where使用括号括起来
                MultiAndExpression bracket_where = null;
                if (where != null) {
                    bracket_where = new MultiAndExpression(Collections.singletonList(where));
                }
                final AtomicLong last_up = new AtomicLong(Long.MIN_VALUE);
                for (int i = 0; i < totalPage; i++) {
                    if (i > 0) {
                        // 有where
                        if (bracket_where != null) {
                            plainSelect.setWhere(new AndExpression(bracket_where, CCJSqlParserUtil.parseExpression("id > " + last_up.get())));
                        }
                        // 无where
                        else {
                            plainSelect.setWhere(CCJSqlParserUtil.parseExpression("id > " + last_up.get()));
                        }
                    }
                    final List<Map<String, Object>> list = jdbcTemplate.queryForList(String.format("%s limit %d, %d", select, 0, PAGE_SIZE));

                    final Map<String, Object> last = list.get(list.size() - 1);
                    last_up.set(Long.parseLong(String.valueOf(last.get(exportSqlDTO.getOffsetColumn()))));
                }
            }
            return null;
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
