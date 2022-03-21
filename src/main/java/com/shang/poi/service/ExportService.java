package com.shang.poi.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.shang.poi.config.TimestampStringConverter;
import com.shang.poi.dto.ExportSqlDTO;
import com.shang.poi.model.ConnectionConfig;
import com.shang.poi.model.Msg;
import com.shang.poi.pool.JdbcTemplatePool;
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
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Created by shangwei2009@hotmail.com on 2022/3/16 14:01
 */
@Service
@Slf4j
public class ExportService {
    // PAGE_SIZE << MAX_ROW
    private static final long PAGE_SIZE = 10000;

    public static final String COUNT_ONE = "count(*)";

    private static final int MAX_ROW = 1000000;

    private static final List<SelectItem> COUNT_ONE_ITEMS;

    static {
        try {
            COUNT_ONE_ITEMS = Collections.singletonList(new SelectExpressionItem(CCJSqlParserUtil.parseExpression(COUNT_ONE)));
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String SHEET_NAME = "Sheet";

    // 总共20个线程（查询）
    private static final int WORKER_THREAD = 20;

    /* 负责执行客户端代码的线程池，根据《Java 开发手册》不可用 Executor 创建，有 OOM 的可能 */
    private static final ExecutorService POOL = new ThreadPoolExecutor(WORKER_THREAD, WORKER_THREAD,
            0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(WORKER_THREAD),
            // 自定义的RejectedExecutionHandler，当队列满时会阻塞SENDER.execute操作（添加任务），参考https://stackoverflow.com/questions/10353173/how-can-i-make-threadpoolexecutor-command-wait-if-theres-too-much-data-it-needs
            (r, executor) -> {
                // this will block if the queue is full
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
    // 以上队列最大任务容纳数量是WORKER_THREAD，当所有线程繁忙时，新的任务会触发异常

    @Resource
    private TimestampStringConverter timestampStringConverter;

    @Resource
    private StorageService storageService;

    public void export(ExportSqlDTO exportSqlDTO, ConnectionConfig config, AtomicBoolean exit, ArrayBlockingQueue<Msg> logging) {
        final ArrayBlockingQueue<List<Map<String, Object>>> result_queue = new ArrayBlockingQueue<>(16);
        final AtomicBoolean queryEnd = new AtomicBoolean(false);
        final String rawSql = StringUtils.trimTrailingCharacter(StringUtils.trimTrailingWhitespace(exportSqlDTO.getSql()), ';');
        final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(config.getId());

        final Path exportFile = storageService.generate();
        ExcelWriter writer = null;
        Path tempXlsx = null;
        try {
            final Statement statement;
            try {
                statement = CCJSqlParserUtil.parse(rawSql);
                if (!(statement instanceof Select)) {
                    enqueue(logging, new Msg(false, "暂时只能处理select"));
                    throw new RuntimeException("暂时只能处理select");
                }
            } catch (JSQLParserException e) {
                enqueue(logging, new Msg(false, "本系统不能处理该SQL语句"));
                throw new RuntimeException("本系统不能处理该SQL语句", e);
            }
            final Select select = (Select) statement;

            final PlainSelect plainSelect = select.getSelectBody(PlainSelect.class);
            final Limit limit = plainSelect.getLimit();
            // 包含limit
            if (limit != null) {
                final LongValue rowCount = limit.getRowCount(LongValue.class);
                if (rowCount.getValue() > PAGE_SIZE * 10) {
                    enqueue(logging, new Msg(false, "页大小不能超过" + (PAGE_SIZE * 10)));
                    throw new RuntimeException("页大小不能超过" + (PAGE_SIZE * 10));
                }
                // 不分页，直接查
                POOL.submit(() -> {
                    try {
                        enqueue(logging, new Msg("SQL:\t\t" + rawSql));
                        final List<Map<String, Object>> list = jdbcTemplate.queryForList(rawSql);
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

                enqueue(logging, new Msg("SQL:\t\t" + select));
                final Map<String, Object> countMap = jdbcTemplate.queryForMap(select.toString());
                // 恢复
                plainSelect.setSelectItems(selectItems);
                final Long size = (Long) countMap.get(COUNT_ONE);
                enqueue(logging, new Msg("总数:\t\t" + size));
                log.info("总数: {}", size);
                final long totalPage = totalPage(size);
                // 分页查
                // 普通分页
                if (!StringUtils.hasText(exportSqlDTO.getOffsetColumn())) {
                    POOL.submit(() -> {
                        try {
                            for (int i = 0; i < totalPage; i++) {
                                if (exit.get()) {
                                    break;
                                }
                                enqueue(logging, new Msg(String.format("查询第\t\t%s页", i + 1)));
                                log.info("查询第{}页", i + 1);
                                final String sql = String.format("%s limit %d, %d", rawSql, i * PAGE_SIZE, PAGE_SIZE);
                                enqueue(logging, new Msg("SQL:\t\t" + sql));
                                final List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
                                result_queue.put(list);
                                if (list.size() < PAGE_SIZE) {
                                    // 避免select count
                                    break;
                                }
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
                                if (exit.get()) {
                                    break;
                                }
                                enqueue(logging, new Msg(String.format("查询第\t\t%s页", i + 1)));
                                log.info("查询第{}页", i + 1);
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
                                final String sql = String.format("%s limit %d, %d", select, 0, PAGE_SIZE);
                                enqueue(logging, new Msg("SQL:\t\t" + sql));
                                final List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
                                result_queue.put(list);
                                // FIXME: 2022/1/25 注意可能有size=0的情况
                                final Map<String, Object> last = list.get(list.size() - 1);
                                last_up.set(Long.parseLong(String.valueOf(last.get(exportSqlDTO.getOffsetColumn()))));
                                if (list.size() < PAGE_SIZE) {
                                    // 避免select count
                                    break;
                                }
                            }
                        } catch (InterruptedException | JSQLParserException e) {
                            log.error(e.getLocalizedMessage(), e);
                        } finally {
                            queryEnd.set(true);
                        }
                    });

                }
            }
//            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//            response.setCharacterEncoding("UTF-8");
//            final String fileName = UriUtils.encode("Export.xlsx", "UTF-8");
//            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName);
            final AtomicBoolean headInitialized = new AtomicBoolean(false);

            tempXlsx = Files.createTempFile("spring_poi_", ".xlsx");
            final ExcelWriterBuilder builder = EasyExcel.write(tempXlsx.toFile()).registerConverter(timestampStringConverter);
            final AtomicLong total = new AtomicLong(0); // 包含头
            final AtomicLong total_sheet = new AtomicLong(1); // 默认1个
            final AtomicBoolean hasHead = new AtomicBoolean(false); // 默认无
            WriteSheet sheet = null;
            while (!queryEnd.get() || !result_queue.isEmpty()) {
                final List<Map<String, Object>> result = result_queue.poll(5, TimeUnit.SECONDS);
                if (result != null) {
                    if (!headInitialized.get()) {
                        final Map<String, Object> map = CollectionUtils.firstElement(result);
                        if (map != null) {
                            // 有数据，添加头
                            final List<List<String>> head = map.keySet().stream().map(Collections::singletonList).collect(Collectors.toList());
                            builder.head(head);
                            hasHead.set(true);
                            total.addAndGet(1);
                        }
                        // 初始化writer
                        writer = builder.build();
                        sheet = EasyExcel.writerSheet(SHEET_NAME + "_" + total_sheet.get()).sheetNo((int) total_sheet.get() - 1).build();
                        headInitialized.set(true);
                    }
                    enqueue(logging, new Msg(String.format("写入\t\t%s条数据", result.size())));
                    log.info("写入{}条数据", result.size());

                    if (result.size() + total.get() > total_sheet.get() * MAX_ROW) {
                        total_sheet.addAndGet(1); // 新增一页
                        total.addAndGet(result.size() + (hasHead.get() ? 1 : 0));
                        final long thisPageRows = total_sheet.get() * MAX_ROW - (result.size() + total.get());
                        final List<ArrayList<Object>> thisPage = result.stream().map(e -> new ArrayList<>(e.values())).limit(thisPageRows).collect(Collectors.toList());
                        final List<ArrayList<Object>> nextPage = result.stream().map(e -> new ArrayList<>(e.values())).skip(thisPageRows).collect(Collectors.toList());
                        if (writer != null && sheet != null) {
                            writer.write(thisPage, sheet);
                            sheet = EasyExcel.writerSheet(SHEET_NAME + "_" + total_sheet.get()).sheetNo((int) total_sheet.get() - 1).build();
                            writer.write(nextPage, sheet);
                        }
                    } else {
                        final List<List<Object>> collect = result.stream().map(e -> new ArrayList<>(e.values())).collect(Collectors.toList());
                        total.addAndGet(result.size());
                        if (writer != null && sheet != null) {
                            writer.write(collect, sheet);
                        }
                    }
                    enqueue(logging, new Msg(String.format("累计写入\t%s条数据", total.get() - totalSheet(total.get()))));
                    log.info("累计写入{}条数据", total.get() - totalSheet(total.get()));
                }
            }
        } catch (InterruptedException | IOException e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.finish();
                    Files.move(tempXlsx, exportFile);
                    enqueue(logging, new Msg(false, "得到文件:\t" + exportFile.getFileName().toString()));
                } catch (Exception e) {
                    log.error(e.getLocalizedMessage(), e);
                }
            }
            result_queue.clear(); // 先设置为exit，再清空结果
        }
    }

    private void enqueue(ArrayBlockingQueue<Msg> queue, Msg msg) {
        if (queue.remainingCapacity() < 1) {
            queue.poll();
        }
        queue.offer(msg);
    }

    private Long totalPage(Long size) {
        final long page = size / PAGE_SIZE;
        if (page * PAGE_SIZE < size) {
            return page + 1;
        } else {
            return page;
        }
    }

    private long totalSheet(long total) {
        final long page = total / MAX_ROW;
        if (page * MAX_ROW < total) {
            return page + 1;
        } else {
            return page;
        }
    }

}
