package com.ll.simpleDb;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sql {
    private final SimpleDb simpleDb;
    private final StringBuilder sqlBuilder = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    public Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    public Sql append(String sqlPart) {
        if (!sqlBuilder.isEmpty()) {
            sqlBuilder.append("\n");
        }

        sqlBuilder.append(sqlPart);
        return this;
    }

    public Sql append(String sqlPart, Object... params) {
        append(sqlPart);
        this.params.addAll(Arrays.asList(params));
        return this;
    }

    public long insert() {
        String sql = getSql();
        printSql(sql);

        try (
                Connection conn = simpleDb.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            bindParams(stmt);
            stmt.executeUpdate();

            return getGeneratedKey(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("INSERT 실패: " + sql, e);
        }
    }

    public int update() {
        return executeUpdate("UPDATE 실패");
    }

    public int delete() {
        return executeUpdate("DELETE 실패");
    }

    private int executeUpdate(String errorMessage) {
        String sql = getSql();
        printSql(sql);

        try (
                Connection conn = simpleDb.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            bindParams(stmt);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(errorMessage + ": " + sql, e);
        }
    }

    private String getSql() {
        return sqlBuilder.toString();
    }

    private void printSql(String sql) {
        if (!simpleDb.isDevMode()) return;

        System.out.println("== rawSql ==");
        System.out.println(sql);
        System.out.println("params = " + params);
    }

    private void bindParams(PreparedStatement stmt) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    private long getGeneratedKey(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getLong(1);
            }

            return 0;
        }
    }
}