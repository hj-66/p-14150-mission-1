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

    public SimpleDb(String host, String username, String password, String dbName) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
    }

    public Connection getConnection() {
        String url = "jdbc:mysql://%s:3306/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
                .formatted(host, dbName);

        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new RuntimeException("DB 연결 실패", e);
        }
    }

    public void run(String sql, Object... params) {
        if (devMode) {
            System.out.println("SQL 실행: " + sql);
            System.out.println("파라미터: " + java.util.Arrays.toString(params));
        }

        try (
                Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            // 파라미터 바인딩
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            stmt.executeUpdate(); // INSERT, UPDATE, DELETE

        } catch (SQLException e) {
            throw new RuntimeException("SQL 실행 실패: " + sql, e);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }
}
