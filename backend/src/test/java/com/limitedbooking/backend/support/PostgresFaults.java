package com.limitedbooking.backend.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** 테스트 소유 컨테이너만 조작하며 관리 연결은 애플리케이션 풀과 분리한다. */
public final class PostgresFaults implements AutoCloseable {
    private final PostgreSQLContainer postgres;
    private final Connection management;
    private boolean connectionsBlocked;
    private boolean paused;

    public PostgresFaults(PostgreSQLContainer postgres) {
        this.postgres = postgres;
        String url = postgres.getJdbcUrl();
        int query = url.indexOf('?');
        int databaseEnd = query < 0 ? url.length() : query;
        int databaseStart = url.lastIndexOf('/', databaseEnd - 1) + 1;
        String managementUrl = url.substring(0, databaseStart) + "postgres" + url.substring(databaseEnd);
        try {
            management = DriverManager.getConnection(managementUrl, postgres.getUsername(), postgres.getPassword());
        } catch (SQLException failure) {
            throw new IllegalStateException("시험 관리 연결 생성 실패", failure);
        }
    }

    public void blockAndTerminateConnections() {
        connectionsBlocked = true; // 명령 중 실패해도 close가 복구를 시도한다.
        allowConnections(false);
        try (var statement = management.prepareStatement("""
                SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                WHERE datname = ? AND pid <> pg_backend_pid()
                """)) {
            statement.setString(1, postgres.getDatabaseName());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (!rows.getBoolean(1)) throw new IllegalStateException("시험 DB 연결 종료 실패");
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("시험 DB 연결 차단·종료 실패", failure);
        }
    }

    public void restoreConnections() {
        if (!connectionsBlocked) return;
        allowConnections(true);
        connectionsBlocked = false;
    }

    private void allowConnections(boolean allow) {
        String database = postgres.getDatabaseName().replace("\"", "\"\"");
        try (var statement = management.createStatement()) {
            statement.execute("ALTER DATABASE \"" + database + "\" ALLOW_CONNECTIONS " + allow);
        } catch (SQLException failure) {
            throw new IllegalStateException("시험 DB 연결 허용 설정 실패", failure);
        }
    }

    public void pause() {
        paused = true;
        DockerClientFactory.instance().client().pauseContainerCmd(postgres.getContainerId()).exec();
    }

    public void unpause() {
        if (!paused) return;
        DockerClientFactory.instance().client().unpauseContainerCmd(postgres.getContainerId()).exec();
        paused = false;
    }

    public AutoCloseable lockResourceRow(UUID resourceId) {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM resources WHERE id=? FOR UPDATE")) {
                statement.setObject(1, resourceId);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("잠글 시험 대상 없음");
                }
            }
            Connection locked = connection;
            return () -> {
                try (locked) {
                    locked.rollback();
                }
            };
        } catch (SQLException | RuntimeException failure) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw new IllegalStateException("시험 대상 행 잠금 실패", failure);
        }
    }

    @Override public void close() throws SQLException {
        // pause 해제 후에만 관리 SQL을 실행한다. 실패해도 나머지 정리는 시도한다.
        Throwable failure = null;
        try { unpause(); } catch (RuntimeException cleanup) { failure = cleanup; }
        try { restoreConnections(); } catch (RuntimeException cleanup) {
            if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
        }
        try { management.close(); } catch (SQLException cleanup) {
            if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
        }
        if (failure instanceof SQLException sql) throw sql;
        if (failure instanceof RuntimeException runtime) throw runtime;
    }
}
