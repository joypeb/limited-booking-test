package com.limitedbooking.backend.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 업무 V1을 적용하는 실행 소유 DB. 연결 fallback이 없다. */
public final class SchemaDatabase implements AutoCloseable {
    private final ContainerTestSupport containers;
    private final DataSource dataSource;
    public SchemaDatabase() {
        containers = ContainerTestSupport.postgresOnly();
        dataSource = new DriverManagerDataSource(containers.postgres.getJdbcUrl(),
                containers.postgres.getUsername(), containers.postgres.getPassword());
        try {
            var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .executeInTransaction(true).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc().queryForObject("SELECT current_database()",String.class)).isEqualTo(containers.postgres.getDatabaseName());
        } catch (RuntimeException | Error error) {
            try {close();} catch(RuntimeException cleanup) {error.addSuppressed(cleanup);} throw error;
        }
    }
    public DataSource dataSource() { return dataSource; }
    public JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    public void reset() { jdbc().execute("DELETE FROM reservations");jdbc().execute("DELETE FROM resources"); }
    public Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var table:List.of("resources","reservations","flyway_schema_history"))
            result.put(table,jdbc().queryForList("SELECT * FROM "+table+" ORDER BY 1"));
        return result;
    }
    public List<Map<String,Object>> structure() {
        return jdbc().queryForList("SELECT table_name,column_name,data_type,is_nullable,character_maximum_length,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('resources','reservations') ORDER BY table_name,ordinal_position");
    }
    @Override public void close() { containers.close(); }
}
