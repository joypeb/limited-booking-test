package com.limitedbooking.backend.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 업무 V1을 적용하는 실행 소유 DB. 연결 fallback이 없다. */
public final class SchemaDatabase implements AutoCloseable {
    public enum Mode { FRESH, REAPPLIED }
    private final ContainerTestSupport containers;
    private final DataSource dataSource;
    public SchemaDatabase() { this(Mode.FRESH); }
    public SchemaDatabase(Mode mode) {
        containers = ContainerTestSupport.postgresOnly();
        dataSource = new DriverManagerDataSource(containers.postgres.getJdbcUrl(),
                containers.postgres.getUsername(), containers.postgres.getPassword());
        try {
            var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .executeInTransaction(true).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc().queryForObject("SELECT current_database()",String.class)).isEqualTo(containers.postgres.getDatabaseName());
            if(mode == Mode.REAPPLIED) {
                var resource = UUID.randomUUID();
                var time = java.time.OffsetDateTime.parse("2026-10-14T01:02:03.123456Z");
                jdbc().update("INSERT INTO resources VALUES (?,?,?,?,?)",resource,"reapply sentinel",10,9,time);
                jdbc().update("INSERT INTO reservations VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),resource,UUID.randomUUID(),"sentinel","CONFIRMED",time,null);
                var before = snapshot();var structure = structure();
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                assertThat(snapshot()).isEqualTo(before);assertThat(structure()).isEqualTo(structure);
                reset();
            }
            System.out.println("업무 DB 준비 database="+containers.postgres.getDatabaseName()+" mode="+mode+" 최초 migration=1"+(mode == Mode.REAPPLIED?" validate=성공 재적용=0 표식/이력/구조=보존":""));
        } catch (RuntimeException | Error error) {
            try {close();} catch(RuntimeException cleanup) {error.addSuppressed(cleanup);} throw error;
        }
    }
    public DataSource dataSource() { return dataSource; }
    public JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    public void reset() { new TestData(dataSource).reset(); }
    public Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var table:List.of("resources","reservations","flyway_schema_history"))
            result.put(table,jdbc().queryForList("SELECT * FROM "+table+" ORDER BY 1"));
        return result;
    }
    public Map<String,List<Map<String,Object>>> structure() {
        var result = new LinkedHashMap<String,List<Map<String,Object>>>();
        result.put("columns", jdbc().queryForList("SELECT table_name,column_name,data_type,is_nullable,character_maximum_length,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('resources','reservations') ORDER BY table_name,ordinal_position"));
        result.put("constraints", jdbc().queryForList("SELECT conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid IN ('resources'::regclass,'reservations'::regclass) ORDER BY conname"));
        result.put("indexes", jdbc().queryForList("SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' AND tablename IN ('resources','reservations') ORDER BY indexname"));
        return result;
    }
    @Override public void close() { containers.close(); }
}
