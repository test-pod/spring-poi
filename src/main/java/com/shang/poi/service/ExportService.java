package com.shang.poi.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.shang.poi.config.TimestampStringConverter;
import com.shang.poi.dto.ExportSqlDTO;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
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

    // ??????20?????????????????????
    private static final int WORKER_THREAD = 20;

    /* ???????????????????????????????????????????????????Java ???????????????????????? Executor ???????????? OOM ????????? */
    private static final ExecutorService POOL = new ThreadPoolExecutor(WORKER_THREAD, WORKER_THREAD,
            0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(WORKER_THREAD),
            // ????????????RejectedExecutionHandler???????????????????????????SENDER.execute?????????????????????????????????https://stackoverflow.com/questions/10353173/how-can-i-make-threadpoolexecutor-command-wait-if-theres-too-much-data-it-needs
            (r, executor) -> {
                // this will block if the queue is full
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
    // ???????????????????????????????????????WORKER_THREAD?????????????????????????????????????????????????????????

    @Resource
    private TimestampStringConverter timestampStringConverter;

    @Resource
    private StorageService storageService;

    public void export(SseEmitter sseEmitter, ExportSqlDTO exportSqlDTO, AtomicBoolean exit) {
        final ArrayBlockingQueue<List<Map<String, Object>>> result_queue = new ArrayBlockingQueue<>(16);
        final AtomicBoolean queryEnd = new AtomicBoolean(false);
        final String rawSql = StringUtils.trimTrailingCharacter(StringUtils.trimTrailingWhitespace(exportSqlDTO.getSql()), ';');
        final JdbcTemplate jdbcTemplate = JdbcTemplatePool.get(exportSqlDTO.getId());

        final Path exportFile = storageService.generate();
        ExcelWriter writer = null;
        try {
            final Statement statement;
            try {
                statement = CCJSqlParserUtil.parse(rawSql);
                if (!(statement instanceof Select)) {
                    sendSse(sseEmitter, new Msg(false, "??????????????????select"));
                    throw new RuntimeException("??????????????????select");
                }
            } catch (JSQLParserException e) {
                sendSse(sseEmitter, new Msg(false, "????????????????????????SQL??????"));
                throw new RuntimeException("????????????????????????SQL??????", e);
            }
            final Select select = (Select) statement;

            final PlainSelect plainSelect = select.getSelectBody(PlainSelect.class);
            final Limit limit = plainSelect.getLimit();
            // ??????limit
            if (limit != null) {
                final LongValue rowCount = limit.getRowCount(LongValue.class);
                if (rowCount.getValue() > PAGE_SIZE * 10) {
                    sendSse(sseEmitter, new Msg(false, "?????????????????????" + (PAGE_SIZE * 10)));
                    throw new RuntimeException("?????????????????????" + (PAGE_SIZE * 10));
                }
                // ?????????????????????
                POOL.submit(() -> {
                    try {
                        sendSse(sseEmitter, new Msg("SQL:\t\t" + rawSql));
                        final List<Map<String, Object>> list = jdbcTemplate.queryForList(rawSql);
                        result_queue.put(list);
                    } catch (InterruptedException e) {
                        log.error(e.getLocalizedMessage(), e);
                    } finally {
                        queryEnd.set(true);
                    }
                });
            }
            // ?????????limit
            else {
                final List<SelectItem> selectItems = plainSelect.getSelectItems();
                // ??????
                plainSelect.setSelectItems(COUNT_ONE_ITEMS);

                sendSse(sseEmitter, new Msg("SQL:\t\t" + select));
                final Map<String, Object> countMap = jdbcTemplate.queryForMap(select.toString());
                // ??????
                plainSelect.setSelectItems(selectItems);
                final Long size = (Long) countMap.get(COUNT_ONE);
                sendSse(sseEmitter, new Msg("??????:\t\t" + size));
                log.info("??????: {}", size);
                final long totalPage = totalPage(size);
                // ?????????
                // ????????????
                if (!StringUtils.hasText(exportSqlDTO.getOffsetColumn())) {
                    POOL.submit(() -> {
                        try {
                            for (int i = 0; i < totalPage; i++) {
                                if (exit.get()) {
                                    break;
                                }
                                sendSse(sseEmitter, new Msg(String.format("?????????\t\t%s???", i + 1)));
                                log.info("?????????{}???", i + 1);
                                final String sql = String.format("%s limit %d, %d", rawSql, i * PAGE_SIZE, PAGE_SIZE);
                                sendSse(sseEmitter, new Msg("SQL:\t\t" + sql));
                                final List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
                                result_queue.put(list);
                                if (list.size() < PAGE_SIZE) {
                                    // ??????select count
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
                // ?????????????????????????????????????????????
                else {
                    final List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
                    // ??????order by???
                    final Optional<OrderByElement> orderByColumnOptional = Optional.ofNullable(orderByElements)
                            .flatMap(list -> list.stream().filter(orderByElement -> {
                                final Expression expression = orderByElement.getExpression();
                                if (expression instanceof Column) {
                                    return Objects.equals(exportSqlDTO.getOffsetColumn(), ((Column) expression).getColumnName());
                                }
                                return false;
                            }).findAny());
                    // ?????????????????????????????????????????????order by???
                    if (orderByColumnOptional.isPresent()) {
                        // ??????
                        orderByColumnOptional.get().setAsc(true);
                    }
                    // ???????????????????????? ??? ?????????order by
                    else {
                        try {
                            // ??????
                            plainSelect.addOrderByElements(new OrderByElement().withExpression(CCJSqlParserUtil.parseExpression(exportSqlDTO.getOffsetColumn())).withAsc(true));
                        } catch (JSQLParserException e) {
                            log.error(e.getLocalizedMessage(), e);
                        }
                    }

                    final Expression where = plainSelect.getWhere();
                    POOL.submit(() -> {
                        try {
                            // ??????where?????????????????????
                            MultiAndExpression bracket_where = null;
                            if (where != null) {
                                bracket_where = new MultiAndExpression(Collections.singletonList(where));
                            }
                            final AtomicLong last_up = new AtomicLong(Long.MIN_VALUE);
                            for (int i = 0; i < totalPage; i++) {
                                if (exit.get()) {
                                    break;
                                }
                                sendSse(sseEmitter, new Msg(String.format("?????????\t\t%s???", i + 1)));
                                log.info("?????????{}???", i + 1);
                                if (i > 0) {
                                    // ???where
                                    if (bracket_where != null) {
                                        plainSelect.setWhere(new AndExpression(bracket_where, CCJSqlParserUtil.parseExpression("id > " + last_up.get())));
                                    }
                                    // ???where
                                    else {
                                        plainSelect.setWhere(CCJSqlParserUtil.parseExpression("id > " + last_up.get()));
                                    }
                                }
                                final String sql = String.format("%s limit %d, %d", select, 0, PAGE_SIZE);
                                sendSse(sseEmitter, new Msg("SQL:\t\t" + sql));
                                final List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
                                result_queue.put(list);
                                // FIXME: 2022/1/25 ???????????????size=0?????????
                                final Map<String, Object> last = list.get(list.size() - 1);
                                last_up.set(Long.parseLong(String.valueOf(last.get(exportSqlDTO.getOffsetColumn()))));
                                if (list.size() < PAGE_SIZE) {
                                    // ??????select count
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

            final ExcelWriterBuilder builder = EasyExcel.write(exportFile.toFile()).registerConverter(timestampStringConverter);
            final AtomicLong total = new AtomicLong(0); // ?????????
            final AtomicLong total_sheet = new AtomicLong(1); // ??????1???
            final AtomicBoolean hasHead = new AtomicBoolean(false); // ?????????
            WriteSheet sheet = null;
            while (!queryEnd.get() || !result_queue.isEmpty()) {
                final List<Map<String, Object>> result = result_queue.poll(5, TimeUnit.SECONDS);
                if (result != null) {
                    if (!headInitialized.get()) {
                        final Map<String, Object> map = CollectionUtils.firstElement(result);
                        if (map != null) {
                            // ?????????????????????
                            final List<List<String>> head = map.keySet().stream().map(Collections::singletonList).collect(Collectors.toList());
                            builder.head(head);
                            hasHead.set(true);
                            total.addAndGet(1);
                        }
                        // ?????????writer
                        writer = builder.build();
                        sheet = EasyExcel.writerSheet(SHEET_NAME + "_" + total_sheet.get()).sheetNo((int) total_sheet.get() - 1).build();
                        headInitialized.set(true);
                    }
                    sendSse(sseEmitter, new Msg(String.format("??????\t\t%s?????????", result.size())));
                    log.info("??????{}?????????", result.size());

                    if (result.size() + total.get() > total_sheet.get() * MAX_ROW) {
                        total_sheet.addAndGet(1); // ????????????
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
                    sendSse(sseEmitter, new Msg(String.format("????????????\t%s?????????", total.get() - totalSheet(total.get()))));
                    log.info("????????????{}?????????", total.get() - totalSheet(total.get()));
                }
            }
        } catch (InterruptedException e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.finish();
                    sendSse(sseEmitter, new Msg(false, "????????????:\t" + exportFile.getFileName().toString()));
                } catch (Exception e) {
                    log.error(e.getLocalizedMessage(), e);
                }
            }
            result_queue.clear(); // ????????????exit??????????????????
        }
    }

    private void sendSse(SseEmitter sseEmitter, Msg msg) {
        try {
            sseEmitter.send(msg);
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
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

    private long totalSheet(long total) {
        final long page = total / MAX_ROW;
        if (page * MAX_ROW < total) {
            return page + 1;
        } else {
            return page;
        }
    }

}
