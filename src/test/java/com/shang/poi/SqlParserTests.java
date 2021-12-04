package com.shang.poi;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@Slf4j
public class SqlParserTests {
    @Test
    public void test01() throws JSQLParserException {
        final Statement statement = CCJSqlParserUtil.parse("select * from mock where name like 's%' and id > 100 limit 0, 100");
        if (statement instanceof Select) {
            final Select select = (Select) statement;
            final SelectBody selectBody = select.getSelectBody();
            log.info("selectBody: {}", selectBody);
            if (selectBody instanceof PlainSelect) {
                final PlainSelect plainSelect = (PlainSelect) selectBody;
                log.info("selectItems: {}", plainSelect.getSelectItems());
                final SelectExpressionItem selectExpressionItem = new SelectExpressionItem(CCJSqlParserUtil.parseExpression("count(1)"));
                plainSelect.setSelectItems(Collections.singletonList(selectExpressionItem));
                log.info("limit: {}", plainSelect.getLimit());
                final Limit limit = new Limit().withOffset(CCJSqlParserUtil.parseExpression("100")).withRowCount(CCJSqlParserUtil.parseExpression("100"));
                plainSelect.setLimit(limit);
            }
        }
        log.info("sql: {}", statement);
    }
}
