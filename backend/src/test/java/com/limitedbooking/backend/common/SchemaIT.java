package com.limitedbooking.backend.common;

import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.SchemaDatabases;
import com.limitedbooking.backend.support.SchemaDatabase.Mode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaIT {
    SchemaDatabases databases;
    DataSource dataSource;
    static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-14T01:02:03.123456Z");
    @BeforeAll void prepare() { databases = new SchemaDatabases(); }
    @AfterAll void close() { if (databases != null) databases.close(); }
    void use(Mode mode) { dataSource = databases.use(mode).dataSource(); }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsInvalidAvailability(Mode mode) throws Exception {
        use(mode);
        for (int[] pair : new int[][]{{10,-1},{10,11},{-1,0},{1001,0}}) {
            var row = resource(); row.put("capacity",pair[0]);row.put("available",pair[1]);
            rejected("resources",row,"23514",null);
        }
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void acceptsAvailabilityBoundaries(Mode mode) throws Exception {
        use(mode);
        for (int[] pair : new int[][]{{0,0},{1000,1000},{10,0},{10,10}}) {
            var row = resource(); row.put("capacity",pair[0]);row.put("available",pair[1]);insert("resources",row);
            try (var c=dataSource.getConnection();var st=c.prepareStatement("SELECT capacity,available FROM resources WHERE id=?")) {
                st.setObject(1,row.get("id"));try(var rs=st.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isEqualTo(pair[0]);assertThat(rs.getInt(2)).isEqualTo(pair[1]);}
            }
        }
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsResourceNull_id(Mode mode) throws Exception {
        use(mode);
        var row=resource(); row.put("id",null); rejected("resources",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsResourceNull_name(Mode mode) throws Exception {
        use(mode);
        var row=resource(); row.put("name",null); rejected("resources",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsResourceNull_capacity(Mode mode) throws Exception {
        use(mode);
        var row=resource(); row.put("capacity",null); rejected("resources",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsResourceNull_available(Mode mode) throws Exception {
        use(mode);
        var row=resource(); row.put("available",null); rejected("resources",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsResourceNull_created_at(Mode mode) throws Exception {
        use(mode);
        var row=resource(); row.put("created_at",null); rejected("resources",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesResourcePrimaryKey(Mode mode) throws Exception {
        use(mode);
        var row=resource();insert("resources",row);var duplicate=new LinkedHashMap<>(row);duplicate.put("name","different");
        rejected("resources",duplicate,"23505","resources_pkey");
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesResourceNameLength(Mode mode) throws Exception {
        use(mode);
        for (String character : List.of("a","😀")) {
            var valid=resource();valid.put("name",character.repeat(100));insert("resources",valid);
            assertThat(scalar("SELECT char_length(name) FROM resources WHERE id='"+valid.get("id")+"'")).isEqualTo(100);
            var invalid=resource();invalid.put("name",character.repeat(101));rejected("resources",invalid,"22001",null);
        }
        var spaces=resource();spaces.put("name","a".repeat(100)+" ");insert("resources",spaces);
        assertThat(scalar("SELECT name FROM resources WHERE id='"+spaces.get("id")+"'")).isEqualTo("a".repeat(100));
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesUserKeyUniqueness(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var other=resource();insert("resources",other);
        var first=reservation((UUID)target.get("id"));insert("reservations",first);
        for (Object targetId : List.of(target.get("id"),other.get("id"))) {
            var duplicate=new LinkedHashMap<>(first);duplicate.put("id",UUID.randomUUID());duplicate.put("resource_id",targetId);
            rejected("reservations",duplicate,"23505","uq_reservations_user_key");
        }
        execute("UPDATE reservations SET status='CANCELLED',cancelled_at='2026-10-14T02:00:00Z'");
        var duplicate=new LinkedHashMap<>(first);duplicate.put("id",UUID.randomUUID());
        rejected("reservations",duplicate,"23505","uq_reservations_user_key");
        var differentUser=new LinkedHashMap<>(first);differentUser.put("id",UUID.randomUUID());differentUser.put("user_id",UUID.randomUUID());insert("reservations",differentUser);
        var differentKey=new LinkedHashMap<>(first);differentKey.put("id",UUID.randomUUID());differentKey.put("idempotency_key","key2");insert("reservations",differentKey);
        assertThat(scalar("SELECT count(*) FROM reservations")).isEqualTo(3L);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsUnknownResource(Mode mode) throws Exception {
        use(mode);
        rejected("reservations",reservation(UUID.randomUUID()),"23503","fk_reservations_resource");
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesStatusAndCancellationTime(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);
        for (String status : List.of("CONFIRMED","CANCELLED")) {
            var valid=reservation((UUID)target.get("id"));valid.put("status",status);valid.put("cancelled_at",status.equals("CANCELLED")?TIME.plusHours(1):null);insert("reservations",valid);
            var invalid=reservation((UUID)target.get("id"));invalid.put("status",status);invalid.put("cancelled_at",status.equals("CANCELLED")?null:TIME);
            rejected("reservations",invalid,"23514","ck_reservations_status_cancelled_at");
        }
        var unknown=reservation((UUID)target.get("id"));unknown.put("status","UNKNOWN");rejected("reservations",unknown,"23514","ck_reservations_status_cancelled_at");
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_id(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("id",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_resource_id(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("resource_id",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_user_id(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("user_id",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_idempotency_key(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("idempotency_key",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_status(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("status",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsReservationNull_created_at(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var row=reservation((UUID)target.get("id"));
        row.put("created_at",null);rejected("reservations",row,"23502",null);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesReservationPrimaryKey(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);var first=reservation((UUID)target.get("id"));insert("reservations",first);
        var duplicate=new LinkedHashMap<>(first);duplicate.put("idempotency_key","other");rejected("reservations",duplicate,"23505","reservations_pkey");
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void enforcesKeyLength(Mode mode) throws Exception {
        use(mode);
        var target=resource();insert("resources",target);
        var valid=reservation((UUID)target.get("id"));valid.put("idempotency_key","k".repeat(64));insert("reservations",valid);
        var invalid=reservation((UUID)target.get("id"));invalid.put("idempotency_key","k".repeat(65));rejected("reservations",invalid,"22001",null);
        var spaces=reservation((UUID)target.get("id"));spaces.put("idempotency_key","s".repeat(64)+" ");insert("reservations",spaces);
        assertThat(scalar("SELECT idempotency_key FROM reservations WHERE id='"+spaces.get("id")+"'")).isEqualTo("s".repeat(64));
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void exposesStatusStorageType(Mode mode) throws Exception {
        use(mode);
        assertThat(scalar("SELECT data_type FROM information_schema.columns WHERE table_name='reservations' AND column_name='status'")).isEqualTo("character varying");
        assertThat(scalar("SELECT character_maximum_length FROM information_schema.columns WHERE table_name='reservations' AND column_name='status'")).isEqualTo(16);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void exposesResourceLookupIndex(Mode mode) throws Exception {
        use(mode);
        assertThat(scalar("SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE c.relname='idx_reservations_resource_id' AND i.indisvalid AND i.indpred IS NULL AND NOT i.indisunique AND pg_get_indexdef(i.indexrelid) LIKE '% ON public.reservations USING btree (resource_id)'")).isEqualTo(1L);
    }
    @Test void rollsBackFailedMigrationAndRecoversInNewDatabase(@TempDir Path directory) throws Exception {
        String original;
        try(var stream=getClass().getResourceAsStream("/db/migration/V1__create_reservation_schema.sql")) {
            assertThat(stream).isNotNull();original=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var faulty=directory.resolve("V1__create_reservation_schema.sql");Files.writeString(faulty,original+"\nSELECT 1 / 0;\n");
        String failedDatabase;
        var failed=ContainerTestSupport.postgresOnly();
        try(failed) {
            failedDatabase=failed.postgres.getDatabaseName();
            System.out.println("마이그레이션 실패사본 DB="+failedDatabase+" SQLSTATE 기대=22012");
            var ds=new DriverManagerDataSource(failed.postgres.getJdbcUrl(),failed.postgres.getUsername(),failed.postgres.getPassword());
            var error=catchThrowable(()->Flyway.configure().dataSource(ds).locations("filesystem:"+directory).executeInTransaction(true).load().migrate());
            assertThat(error).isInstanceOf(FlywayException.class);
            Throwable cause=error;while(cause!=null && !(cause instanceof SQLException))cause=cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);assertThat(((SQLException)cause).getSQLState()).isEqualTo("22012");
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname IN ('resources','reservations','resources_pkey','reservations_pkey','uq_reservations_user_key','idx_reservations_resource_id')",Long.class)).isZero();
            if(jdbc.queryForObject("SELECT to_regclass('flyway_schema_history') IS NOT NULL",Boolean.class))
                assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success",Long.class)).isZero();
        }
        assertThat(failed.postgres.isRunning()).isFalse();
        try(var recovered=ContainerTestSupport.postgresOnly()) {
            assertThat(recovered.postgres.getDatabaseName()).isNotEqualTo(failedDatabase);
            System.out.println("마이그레이션 복구 원본 DB="+recovered.postgres.getDatabaseName()+" 실패DB종료=true");
            var ds=new DriverManagerDataSource(recovered.postgres.getJdbcUrl(),recovered.postgres.getUsername(),recovered.postgres.getPassword());
            assertThat(Flyway.configure().dataSource(ds).locations("classpath:db/migration").executeInTransaction(true).load().migrate().migrationsExecuted).isEqualTo(1);
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            var error=catchThrowable(()->jdbc.update("INSERT INTO resources VALUES (?,?,?,?,?)",UUID.randomUUID(),"bad",10,-1,TIME));
            assertThat(error).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resources",Long.class)).isZero();
        }
        assertThat(Files.readString(faulty)).isEqualTo(original+"\nSELECT 1 / 0;\n");
    }
    Map<String,Object> resource() {
        var row=new LinkedHashMap<String,Object>();row.put("id",UUID.randomUUID());row.put("name","예약 대상");row.put("capacity",10);row.put("available",10);row.put("created_at",TIME);return row;
    }
    Map<String,Object> reservation(UUID resourceId) {
        var row=new LinkedHashMap<String,Object>();row.put("id",UUID.randomUUID());row.put("resource_id",resourceId);row.put("user_id",UUID.randomUUID());row.put("idempotency_key","key1");row.put("status","CONFIRMED");row.put("created_at",TIME);row.put("cancelled_at",null);return row;
    }
    void insert(String table, Map<String,Object> row) throws Exception {
        var sql="INSERT INTO "+table+" ("+String.join(",",row.keySet())+") VALUES ("+String.join(",",Collections.nCopies(row.size(),"?"))+")";
        try(var c=dataSource.getConnection();var st=c.prepareStatement(sql)){int index=1;for(var value:row.values())st.setObject(index++,value);st.executeUpdate();}
    }
    void rejected(String table,Map<String,Object> row,String state,String constraint) throws Exception {
        var before=snapshot(table);
        var error=catchThrowable(()->insert(table,row));
        assertThat(error).as("실제 INSERT 거부").isInstanceOf(PSQLException.class);
        var sqlError=(PSQLException)error;assertThat(sqlError.getSQLState()).isEqualTo(state);
        if(constraint!=null)assertThat(sqlError.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
        assertThat(snapshot(table)).as("실패 뒤 새 연결에서 기존 행 보존").isEqualTo(before);
    }
    List<String> snapshot(String table) throws Exception {
        try(var c=dataSource.getConnection();var st=c.createStatement();var rs=st.executeQuery("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY id")) {
            var values=new ArrayList<String>();while(rs.next())values.add(rs.getString(1));return values;
        }
    }
    Object scalar(String sql) throws Exception {
        try(var c=dataSource.getConnection();var st=c.createStatement();var rs=st.executeQuery(sql)){assertThat(rs.next()).isTrue();return rs.getObject(1);}
    }
    void execute(String sql) throws Exception {try(var c=dataSource.getConnection();var st=c.createStatement()){st.execute(sql);}}
}
