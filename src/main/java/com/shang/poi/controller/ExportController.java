package com.shang.poi.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.shang.poi.config.TimestampStringConverter;
import com.shang.poi.dto.ExportSqlDTO;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.pool.JdbcTemplatePool;
import com.shang.poi.service.ConnectionConfigService;
import com.shang.poi.vo.ConnectionConfigVO;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/6 13:44
 */
@Controller
@RequestMapping("/export")
@Validated
@Slf4j
public class ExportController {

    private static final long PAGE_SIZE = 10000;

    public static final String COUNT_ONE = "count(1)";

    private static final List<SelectItem> COUNT_ONE_ITEMS;

    static {
        try {
            COUNT_ONE_ITEMS = Collections.singletonList(new SelectExpressionItem(CCJSqlParserUtil.parseExpression(COUNT_ONE)));
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String SHEET_NAME = "Mock";

    // 总共20个线程（查询+导出）
    private static final int WORKER_THREAD = 20;

    /* 负责执行客户端代码的线程池，根据《Java 开发手册》不可用 Executor 创建，有 OOM 的可能 */
    private static final ExecutorService POOL = new ThreadPoolExecutor(WORKER_THREAD, WORKER_THREAD,
            0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(WORKER_THREAD));
    // 以上队列最大任务容纳数量是WORKER_THREAD，当所有线程繁忙时，新的任务会触发异常

    @Resource
    private ConnectionConfigService connectionConfigService;

    @Resource
    private TimestampStringConverter timestampStringConverter;

    @GetMapping
    public String get(Map<String, Object> map) {
        final List<ConnectionConfigVO> connectionConfigVOS = connectionConfigService.listOnline();
        map.put("connections", connectionConfigVOS);
        return "export";
    }

    @PostMapping
    public void post(HttpServletResponse response, @Valid ExportSqlDTO exportSqlDTO) throws IOException, InterruptedException {
        final ConnectionConfig byId = connectionConfigService.getById(exportSqlDTO.getId());
        if (byId == null) {
            throw new RuntimeException("不存在的Id");
        }
        final ArrayBlockingQueue<List<Map<String, Object>>> result_queue = new ArrayBlockingQueue<>(16);
        final AtomicBoolean queryEnd = new AtomicBoolean(false);
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
            if (rowCount.getValue() > PAGE_SIZE * 10) {
                throw new RuntimeException("页大小不能超过" + (PAGE_SIZE * 10));
            }
            // 不分页，直接查
            POOL.submit(() -> {
                final List<Map<String, Object>> list = jdbcTemplate.queryForList(rawSql);
                try {
                    result_queue.put(list);
                } catch (InterruptedException e) {
                    log.error(e.getLocalizedMessage(), e);
                } finally {
                    queryEnd.set(true);
                }
            });
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
                POOL.submit(() -> {
                    try {
                        for (int i = 0; i < totalPage; i++) {
                            final List<Map<String, Object>> list = jdbcTemplate.queryForList(String.format("%s limit %d, %d", rawSql, i * PAGE_SIZE, PAGE_SIZE));
                            result_queue.put(list);
                        }
                    } catch (InterruptedException e) {
                        log.error(e.getLocalizedMessage(), e);
                    } finally {
                        queryEnd.set(true);
                    }
                });

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
                POOL.submit(() -> {
                    try {
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
                            result_queue.put(list);
                            final Map<String, Object> last = list.get(list.size() - 1);
                            last_up.set(Long.parseLong(String.valueOf(last.get(exportSqlDTO.getOffsetColumn()))));
                        }
                    } catch (InterruptedException | JSQLParserException e) {
                        log.error(e.getLocalizedMessage(), e);
                    } finally {
                        queryEnd.set(true);
                    }
                });

            }
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        final String fileName = URLEncoder.encode("Export.xlsx", "UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName);
        final AtomicBoolean headInitialized = new AtomicBoolean(false);
        ExcelWriter writer = null;
        final ExcelWriterBuilder builder = EasyExcel.write(response.getOutputStream()).registerConverter(timestampStringConverter);
        final WriteSheet sheet = EasyExcel.writerSheet(SHEET_NAME).build();
        while (!queryEnd.get() || !result_queue.isEmpty()) {
            final List<Map<String, Object>> result = result_queue.poll(5, TimeUnit.SECONDS);
            if (result != null) {
                if (!headInitialized.get()) {
                    final Map<String, Object> map = CollectionUtils.firstElement(result);
                    if (map != null) {
                        final List<List<String>> head = map.keySet().stream().map(Collections::singletonList).collect(Collectors.toList());
                        builder.head(head);
                    }
                    headInitialized.set(true);
                }
                writer = builder.build();
                final List<List<Object>> collect = result.stream().map(e -> new ArrayList<>(e.values())).collect(Collectors.toList());
                writer.write(collect, sheet);
            }
        }
        if (writer != null) {
            writer.finish();
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
