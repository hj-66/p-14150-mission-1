package com.ll.simpleDb;

import lombok.Getter;
import lombok.Setter;

import java.sql.*;
import java.util.Arrays;

public class SimpleDb {
    private static final String JDBC_URL_TEMPLATE = "jdbc:mysql://%s:3306/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul";

    private final String host;
    private final String username;
    private final String password;
    private final String dbName;

    @Getter
    @Setter
    private boolean devMode = false;

    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public SimpleDb(String host, String username, String password, String dbName) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
    }

    private Connection makeConnection() {
        try {
            return DriverManager.getConnection(buildJdbcUrl(), username, password);
        } catch (SQLException e) {
            throw new RuntimeException("DB 연결 실패", e);
        }
    }

    private String buildJdbcUrl() {
        return JDBC_URL_TEMPLATE.formatted(host, dbName);
    }

    public Connection getConnection() {
        Connection currentConnection = transactionConnection.get();

        if (isOpen(currentConnection)) {
            return currentConnection;
        }

        return makeConnection();
    }

    public boolean isTransactionActive() {
        return isOpen(transactionConnection.get());
    }

    private boolean isOpen(Connection connection) {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 상태 확인 실패", e);
        }
    }

    public void startTransaction() {
        if (isTransactionActive()) {
            throw new RuntimeException("이미 트랜잭션이 시작되었습니다.");
        }

        try {
            Connection connection = makeConnection();
            connection.setAutoCommit(false);
            transactionConnection.set(connection);

            printDevMessage("트랜잭션 시작");

        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 시작 실패", e);
        }
    }

    public void commit() {
        if (!isTransactionActive()) {
            throw new RuntimeException("진행 중인 트랜잭션이 없습니다.");
        }

        try {
            transactionConnection.get().commit();
            printDevMessage("트랜잭션 커밋");
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 커밋 실패", e);
        } finally {
            closeTransactionConnection();
        }
    }

    public void rollback() {
        if (!isTransactionActive()) {
            throw new RuntimeException("진행 중인 트랜잭션이 없습니다.");
        }

        try {
            transactionConnection.get().rollback();
            printDevMessage("트랜잭션 롤백");
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 롤백 실패", e);
        } finally {
            closeTransactionConnection();
        }
    }

    private void closeTransactionConnection() {
        Connection connection = transactionConnection.get();

        if (connection == null) {
            return;
        }

        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 Connection 종료 실패", e);
        } finally {
            transactionConnection.remove();
        }
    }

    public void run(String sql, Object... params) {
        printRunSql(sql, params);

        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL 실행 실패: " + sql, e);
        } finally {
            closeStatement(statement);
            closeConnectionIfNotInTransaction(connection);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }

    public void close() {
        if (isTransactionActive()) {
            rollback();
        }
    }

    void closeConnectionIfNotInTransaction(Connection connection) {
        if (connection == null || connection == transactionConnection.get()) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("DB Connection 종료 실패", e);
        }
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

    private void printRunSql(String sql, Object[] params) {
        if (!devMode) {
            return;
        }

        System.out.println("SQL 실행: " + sql);
        System.out.println("파라미터: " + Arrays.toString(params));
    }

    private void printDevMessage(String message) {
        if (devMode) {
            System.out.println(message);
        }
    }
}
