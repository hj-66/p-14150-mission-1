package com.ll.simpleDb;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    public List<Map<String, Object>> selectRows() {
        String sql = sqlBuilder.toString();
        printSql(sql);

        List<Map<String, Object>> rows = new ArrayList<>();

        try (
                Connection conn = simpleDb.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            bindParams(stmt);

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new java.util.HashMap<>();

                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);

                        if (value instanceof Timestamp) {
                            value = ((Timestamp) value).toLocalDateTime();
                        }

                        row.put(columnName, value);
                    }

                    rows.add(row);
                }
            }

            return rows;

        } catch (SQLException e) {
            throw new RuntimeException("SELECT 실패: " + sql, e);
        }
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();

        if (rows.isEmpty()) {
            return null; // 또는 예외 던져도 됨 (취향)
        }

        return rows.get(0);
    }

    private Object selectScalar() {
        Map<String, Object> row = selectRow();

        if (row == null || row.isEmpty()) {
            return null;
        }

        return row.values().iterator().next();
    }

    public LocalDateTime selectDatetime() {
        Object value = selectScalar();

        return switch (value) {
            case null -> null;
            case LocalDateTime localDateTime -> localDateTime;
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            default -> throw new RuntimeException("LocalDateTime으로 변환할 수 없습니다: " + value);
        };

    }

    public Long selectLong() {
        Object value = selectScalar();

        return switch (value) {
            case null -> null;
            case Number number -> number.longValue();
            case String s -> Long.parseLong(s);
            default -> throw new RuntimeException("Long으로 변환할 수 없습니다: " + value);
        };

    }

    public String selectString() {
        Object value = selectScalar();

        if (value == null) {
            return null;
        }

        return value.toString();
    }

    public Boolean selectBoolean() {
        Object value = selectScalar();

        return switch (value) {
            case Boolean b -> b;
            case Number number -> number.intValue() != 0;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> null;
        };

    }
}