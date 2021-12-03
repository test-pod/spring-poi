package com.shang.poi;

import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlDynamicParam;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.sql.util.SqlVisitor;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Created by shangwei2009@hotmail.com on 2021/12/1 14:07
 */
@Slf4j
public class CalciteTests {

    @Test
    public void test01() throws SqlParseException {
        final SqlParser.Config config = SqlParser.config().withLex(Lex.MYSQL);
//        final String select = "select * from mock_result_playback where name like 'w%' and id in (select tid from mock_service where mock_name like 'h%') order by id limit 100";
        final String select = "select count(*) from mock_result_playback where name <> 'w%' order by id limit 100";
        final String update = "update mock_result_playback set name = 'hello' where id = 1";
        final SqlParser parser = SqlParser.create(select, config);
        final SqlNode sqlNode = parser.parseStmt();
        log.info("raw: {}", sqlNode);

        /*if (SqlKind.QUERY.contains(sqlNode.getKind())) {
            System.out.println("查询");
            final SqlCall sqlCall = (SqlCall) sqlNode;
            System.err.println(sqlCall.getOperator());
            final SqlSelect sqlSelect = (SqlSelect) sqlNode;
            System.err.println(sqlSelect.getWhere());
        }*/
        if (SqlKind.ORDER_BY.equals(sqlNode.getKind())) {
            final SqlOrderBy sqlOrderBy = (SqlOrderBy) sqlNode;
            log.info("fetch: {}", sqlOrderBy.fetch);
            log.info("offset: {}", sqlOrderBy.offset);
            log.info("orderList: {}", sqlOrderBy.orderList);
            log.info("query: {}", sqlOrderBy.query);
            final SqlNode query = sqlOrderBy.query;
            if (SqlKind.SELECT.equals(query.getKind())) {
                final SqlSelect sqlSelect = (SqlSelect) query;
                log.info("selectList: {}", sqlSelect.getSelectList());
//                sqlSelect.setSelectList(SqlNodeList.of());
//                SqlUtil.andExpressions()
                final SqlCall call = SqlStdOperatorTable.COUNT.createCall(SqlNodeList.of(SqlIdentifier.STAR));
                final SqlNode count = SqlParser.create("count(1)", config).parseExpression();
                log.info("call: {}", count);

                sqlSelect.setSelectList(SqlNodeList.of(count));
                final SqlNode where = sqlSelect.getWhere();
                final SqlBasicCall sqlBasicCall = (SqlBasicCall) where;
                final List<SqlNode> operandList = sqlBasicCall.getOperandList();
                log.info("operator: {}", operandList);
                for (final SqlNode node : operandList) {
                    log.info("node: {}", node.getKind());
                }
                log.info("where: {}", where);
                log.info("parserPosition: {}", where.getParserPosition());
                where.accept(new SqlBasicVisitor<SqlNode>() {
                    @Override
                    public SqlNode visit(SqlLiteral literal) {
                        log.info("literal: {}", literal);
                        return super.visit(literal);
                    }

                    @Override
                    public SqlNode visit(SqlCall call) {
                        log.info("call: {}", call);
                        return super.visit(call);
                    }

                    @Override
                    public SqlNode visit(SqlNodeList nodeList) {
                        log.info("nodeList: {}", nodeList);
                        return super.visit(nodeList);
                    }

                    @Override
                    public SqlNode visit(SqlIdentifier id) {
                        log.info("id: {}", id);
                        return super.visit(id);
                    }

                    @Override
                    public SqlNode visit(SqlDataTypeSpec type) {
                        log.info("type: {}", type);
                        return super.visit(type);
                    }

                    @Override
                    public SqlNode visit(SqlDynamicParam param) {
                        log.info("param: {}", param);
                        return super.visit(param);
                    }

                    @Override
                    public SqlNode visit(SqlIntervalQualifier intervalQualifier) {
                        log.info("intervalQualifier: {}", intervalQualifier);
                        return super.visit(intervalQualifier);
                    }
                });
            }
        }

        log.info("raw: {}", sqlNode.toSqlString(MysqlSqlDialect.DEFAULT).getSql());
        log.info("raw: {}", sqlNode);
    }
}
