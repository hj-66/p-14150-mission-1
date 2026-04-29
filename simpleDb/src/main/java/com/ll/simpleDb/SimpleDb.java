package com.ll.simpleDb;

import lombok.Getter;
import lombok.Setter;

import java.sql.*;

public class SimpleDb {
    private final String host;
    private final String username;
    private final String password;
    private final String dbName;

    @Getter
    @Setter
    private boolean devMode = false;

    // 현재 트랜잭션에서 사용할 Connection
    private Connection transactionConnection;

    public SimpleDb(String host, String username, String password, String dbName) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
    }

    private Connection makeConnection() {
        String url = "jdbc:mysql://%s:3306/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
                .formatted(host, dbName);

        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new RuntimeException("DB 연결 실패", e);
        }
    }

    public Connection getConnection() {
        try {
            // 트랜잭션 중이면 기존 트랜잭션 Connection을 반환
            if (isTransactionActive()) {
                return transactionConnection;
            }

            // 트랜잭션 중이 아니면 새 Connection 생성
            return makeConnection();

        } catch (Exception e) {
            throw new RuntimeException("DB 연결 상태 확인 실패", e);
        }
    }

    public boolean isTransactionActive() {
        try {
            return transactionConnection != null && !transactionConnection.isClosed();
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 상태 확인 실패", e);
        }
    }

    public void startTransaction() {
        if (isTransactionActive()) {
            throw new RuntimeException("이미 트랜잭션이 시작되었습니다.");
        }

        try {
            transactionConnection = makeConnection();

            // 자동 커밋 비활성화
            transactionConnection.setAutoCommit(false);

            if (devMode) {
                System.out.println("트랜잭션 시작");
            }

        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 시작 실패", e);
        }
    }

    public void commit() {
        if (!isTransactionActive()) {
            throw new RuntimeException("진행 중인 트랜잭션이 없습니다.");
        }

        try {
            transactionConnection.commit();

            if (devMode) {
                System.out.println("트랜잭션 커밋");
            }

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
            transactionConnection.rollback();

            if (devMode) {
                System.out.println("트랜잭션 롤백");
            }

        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 롤백 실패", e);
        } finally {
            closeTransactionConnection();
        }
    }

    private void closeTransactionConnection() {
        if (transactionConnection == null) return;

        try {
            transactionConnection.setAutoCommit(true);
            transactionConnection.close();
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 Connection 종료 실패", e);
        } finally {
            transactionConnection = null;
        }
    }


    public void run(String sql, Object... params) {
        if (devMode) {
            System.out.println("SQL 실행: " + sql);
            System.out.println("파라미터: " + java.util.Arrays.toString(params));
        }

        Connection conn = null;
        PreparedStatement stmt = null;

        boolean transactionActive = isTransactionActive();

        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("SQL 실행 실패: " + sql, e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }

                // 트랜잭션 중이 아닐 때만 Connection을 닫는다.
                if (!transactionActive && conn != null) {
                    conn.close();
                }

            } catch (SQLException e) {
                throw new RuntimeException("DB 자원 해제 실패", e);
            }
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
}