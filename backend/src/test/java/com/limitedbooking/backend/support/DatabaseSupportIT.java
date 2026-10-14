package com.limitedbooking.backend.support;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.limitedbooking.backend.support.SchemaDatabases;
import com.limitedbooking.backend.support.SchemaDatabase.Mode;
import com.limitedbooking.backend.reservation.*;
import java.time.*;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseSupportIT {
    SchemaDatabases databases;
    SchemaDatabase database;
    static final Instant TIME=Instant.parse("2026-10-14T01:02:03.123456Z");
    @BeforeAll void open() { databases=new SchemaDatabases(); }
    @AfterAll void close() { if(databases!=null)databases.close(); }

    @Test void connectsToOwnedPostgresWithoutStartingRedis() throws Exception {
        var support = ContainerTestSupport.postgresOnly();
        try (support; var connection = DriverManager.getConnection(support.postgres.getJdbcUrl(),
                support.postgres.getUsername(), support.postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(support.postgres.getDatabaseName());
            assertThat(support.postgres.getMappedPort(5432)).isNotEqualTo(5432);
            assertThat(support.redis.getContainerId()).isNull();
        }
        assertThat(support.postgres.isRunning()).isFalse();
        assertThat(support.redis.getContainerId()).isNull();
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void commitsPreparedDataVisibleToAnotherConnection(Mode mode) {
        database=databases.use(mode);
        var data=new TestData(database.dataSource());var target=UUID.randomUUID();
        data.resource(target,"준비 대상",10,9,TIME);
        var confirmed=new StoredReservation(UUID.randomUUID(),target,UUID.randomUUID(),"confirmed",ReservationStatus.CONFIRMED,TIME,null);
        var cancelled=new StoredReservation(UUID.randomUUID(),target,UUID.randomUUID(),"cancelled",ReservationStatus.CANCELLED,TIME,TIME.plusSeconds(1));
        data.reservation(confirmed);data.reservation(cancelled);
        assertThat(database.jdbc().queryForObject("SELECT count(*) FROM reservations",Long.class)).isEqualTo(2L);
        assertThat(database.jdbc().queryForObject("SELECT name FROM resources WHERE id=?",String.class,target)).isEqualTo("준비 대상");
        assertThat(database.jdbc().<Instant>queryForObject("SELECT created_at FROM reservations WHERE id=?",(rs,n)->rs.getObject(1,OffsetDateTime.class).toInstant(),confirmed.id())).isEqualTo(TIME);
        assertThat(database.jdbc().<Instant>queryForObject("SELECT cancelled_at FROM reservations WHERE id=?",(rs,n)->rs.getObject(1,OffsetDateTime.class).toInstant(),cancelled.id())).isEqualTo(TIME.plusSeconds(1));
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void commitsPreparationEvenWhenCallerTransactionRollsBack(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();
        var outer=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(database.dataSource()));
        outer.executeWithoutResult(status->{
            new TestData(database.dataSource()).resource(target,"커밋된 준비",0,0,TIME);
            try(var c=database.dataSource().getConnection();var st=c.createStatement();var rows=st.executeQuery("SELECT count(*) FROM resources")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(1);
            } catch(SQLException error) {throw new IllegalStateException(error);}
            status.setRollbackOnly();
        });
        assertThat(database.jdbc().queryForObject("SELECT count(*) FROM resources",Long.class)).isEqualTo(1L);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void resetsBothTablesRepeatedlyPreservingHistoryAndStructure(Mode mode) {
        database=databases.use(mode);
        directData();var history=database.snapshot().get("flyway_schema_history");var structure=database.structure();
        var data=new TestData(database.dataSource());
        for(int attempt=0;attempt<2;attempt++){
            data.reset();assertEmpty();assertThat(database.snapshot().get("flyway_schema_history")).isEqualTo(history);assertThat(database.structure()).isEqualTo(structure);
        }
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rollsBackFailedResetAndRetriesAfterFaultRemoval(Mode mode) {
        database=databases.use(mode);
        directData();var before=database.snapshot();var structure=database.structure();
        database.jdbc().execute("CREATE FUNCTION fail_resource_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'reset fault' USING ERRCODE='22012'; END $$");
        database.jdbc().execute("CREATE TRIGGER fail_resource_delete BEFORE DELETE ON resources FOR EACH ROW EXECUTE FUNCTION fail_resource_delete()");
        try {
            var error=catchThrowable(()->new TestData(database.dataSource()).reset());
            assertThat(error).isInstanceOf(DataAccessException.class);
            Throwable cause=error;while(cause!=null && !(cause instanceof SQLException))cause=cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);assertThat(((SQLException)cause).getSQLState()).isEqualTo("22012");
            assertThat(database.snapshot()).isEqualTo(before);assertThat(database.structure()).isEqualTo(structure);
        } finally {
            database.jdbc().execute("DROP TRIGGER fail_resource_delete ON resources");database.jdbc().execute("DROP FUNCTION fail_resource_delete()");
        }
        new TestData(database.dataSource()).reset();assertEmpty();
        assertThat(database.snapshot().get("flyway_schema_history")).isEqualTo(before.get("flyway_schema_history"));assertThat(database.structure()).isEqualTo(structure);
        assertThat(database.jdbc().queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname='fail_resource_delete'",Long.class)).isZero();
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void acceptsMixedStatesAndIgnoresOtherResources(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();directResource(target,10,8);
        directReservation(target,"CONFIRMED");directReservation(target,"CONFIRMED");directReservation(target,"CANCELLED");
        var other=UUID.randomUUID();directResource(other,10,9);directReservation(other,"CONFIRMED");
        new DatabaseAssertions(database.dataSource()).assertInventoryConserved(target);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void acceptsZeroCapacityWithoutReservations(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();directResource(target,0,0);
        new DatabaseAssertions(database.dataSource()).assertInventoryConserved(target);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void acceptsAllCancelledReservations(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();directResource(target,10,10);directReservation(target,"CANCELLED");
        new DatabaseAssertions(database.dataSource()).assertInventoryConserved(target);
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsInventoryMismatch(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();directResource(target,10,9);directReservation(target,"CONFIRMED");directReservation(target,"CONFIRMED");
        assertThatThrownBy(()->new DatabaseAssertions(database.dataSource()).assertInventoryConserved(target))
                .isInstanceOf(AssertionError.class).hasMessageContaining(target.toString());
    }
    @ParameterizedTest(name="{displayName} [{0}]") @EnumSource(Mode.class)
    void rejectsMissingResourceInInventoryAssertion(Mode mode) {
        database=databases.use(mode);
        var target=UUID.randomUUID();
        assertThatThrownBy(()->new DatabaseAssertions(database.dataSource()).assertInventoryConserved(target))
                .isInstanceOf(AssertionError.class).hasMessageContaining(target.toString());
    }
    void directData() {
        var target=UUID.randomUUID();directResource(target,10,9);directReservation(target,"CONFIRMED");directReservation(target,"CANCELLED");
    }
    void directResource(UUID id,int capacity,int available) {
        database.jdbc().update("INSERT INTO resources(id,name,capacity,available,created_at) VALUES (?,?,?,?,?)",id,"대상",capacity,available,TIME.atOffset(ZoneOffset.UTC));
    }
    void directReservation(UUID target,String status) {
        database.jdbc().update("INSERT INTO reservations(id,resource_id,user_id,idempotency_key,status,created_at,cancelled_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),target,UUID.randomUUID(),"key",status,TIME.atOffset(ZoneOffset.UTC),status.equals("CANCELLED")?TIME.plusSeconds(1).atOffset(ZoneOffset.UTC):null);
    }
    void assertEmpty() {
        assertThat(database.jdbc().queryForObject("SELECT count(*) FROM reservations",Long.class)).isZero();
        assertThat(database.jdbc().queryForObject("SELECT count(*) FROM resources",Long.class)).isZero();
    }
}
