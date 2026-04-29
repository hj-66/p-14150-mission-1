package com.ll.simpleDb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

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
        addParams(params);
        return this;
    }

    public Sql appendIn(String sqlPart, Object... params) {
        if (params.length == 0) {
            throw new IllegalArgumentException("appendIn에는 최소 1개 이상의 값이 필요합니다.");
        }

        String placeholders = String.join(", ", Collections.nCopies(params.length, "?"));
        String convertedSqlPart = sqlPart.replace("?", placeholders);

        append(convertedSqlPart);
        addParams(params);

        return this;
    }

    public long insert() {
        String sql = getSql();
        printSql(sql);

        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = simpleDb.getConnection();
            statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            bindParams(statement);
            statement.executeUpdate();

            return getGeneratedKey(statement);
        } catch (SQLException e) {
            throw new RuntimeException("INSERT 실패: " + sql, e);
        } finally {
            closeStatement(statement);
            simpleDb.closeConnectionIfNotInTransaction(connection);
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

        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = simpleDb.getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement);

            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(errorMessage + ": " + sql, e);
        } finally {
            closeStatement(statement);
            simpleDb.closeConnectionIfNotInTransaction(connection);
        }
    }

    public List<Map<String, Object>> selectRows() {
        String sql = getSql();
        printSql(sql);

        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = simpleDb.getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                return convertResultSetToRows(resultSet);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SELECT 실패: " + sql, e);
        } finally {
            closeStatement(statement);
            simpleDb.closeConnectionIfNotInTransaction(connection);
        }
    }

    public <T> List<T> selectRows(Class<T> type) {
        return selectRows().stream()
                .map(row -> mapRowToObject(row, type))
                .toList();
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();

        return rows.isEmpty() ? null : rows.getFirst();
    }

    public <T> T selectRow(Class<T> type) {
        List<T> rows = selectRows(type);
        return rows.isEmpty() ? null : rows.getFirst();
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
            case String str -> Long.parseLong(str);
            default -> throw new RuntimeException("Long으로 변환할 수 없습니다: " + value);
        };
    }

    public List<Long> selectLongs() {
        List<Map<String, Object>> rows = selectRows();
        return rows.stream()
                .map(row -> toLong(firstValue(row)))
                .toList();
    }

    public String selectString() {
        Object value = selectScalar();
        return value == null ? null : value.toString();
    }

    public Boolean selectBoolean() {
        Object value = selectScalar();

        return switch (value) {
            case null -> null;
            case Boolean bool -> bool;
            case Number number -> number.intValue() != 0;
            case String str -> Boolean.parseBoolean(str);
            default -> throw new RuntimeException("Boolean으로 변환할 수 없습니다: " + value);
        };
    }

    private List<Map<String, Object>> convertResultSetToRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            rows.add(convertResultSetToRow(rs, metaData, columnCount));
        }

        return rows;
    }

    private Map<String, Object> convertResultSetToRow(
            ResultSet rs,
            ResultSetMetaData metaData,
            int columnCount
    ) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            Object value = convertValue(rs.getObject(i));

            row.put(columnName, value);
        }

        return row;
    }

    private Object convertValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        if (value instanceof byte[] bytes && bytes.length == 1) {
            return bytes[0] != 0;
        }

        return value;
    }

    private Object selectScalar() {
        Map<String, Object> row = selectRow();
        return row == null ? null : firstValue(row);
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
            return -1;
        }
    }

    private void addParams(Object... params) {
        this.params.addAll(Arrays.asList(params));
    }

    private String getSql() {
        return sqlBuilder.toString();
    }

    private void printSql(String sql) {
        if (!simpleDb.isDevMode()) {
            return;
        }

        System.out.println("== rawSql ==");
        System.out.println(sql);
        System.out.println("params = " + params);
    }

    private <T> T mapRowToObject(Map<String, Object> row, Class<T> type) {
        List<Field> fields = getInstanceFields(type);
        List<Object> values = fields.stream()
                .map(field -> row.get(field.getName()))
                .toList();

        try {
            Constructor<T> constructor = findConstructor(type, values.size());
            constructor.setAccessible(true);

            return constructor.newInstance(values.toArray());
        } catch (Exception e) {
            throw new RuntimeException("객체 변환 실패: " + type.getName(), e);
        }
    }

    private List<Field> getInstanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> Constructor<T> findConstructor(Class<T> type, int parameterCount) {
        return (Constructor<T>) Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("사용 가능한 생성자를 찾을 수 없습니다: " + type.getName()));
    }

    private Object firstValue(Map<String, Object> row) {
        return row.values().iterator().next();
    }

    private Long toLong(Object value) {
        return switch (value) {
            case null -> null;
            case Number number -> number.longValue();
            case String str -> Long.parseLong(str);
            default -> throw new RuntimeException("Long으로 변환할 수 없습니다: " + value);
        };
    }

    private void closeStatement(Statement statement) {
        if (statement == null) {
            return;
        }

        try {
            statement.close();
        } catch (SQLException e) {
            throw new RuntimeException("DB Statement 종료 실패", e);
        }
    }
}
