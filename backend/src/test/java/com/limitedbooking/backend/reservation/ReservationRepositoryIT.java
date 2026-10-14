package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.SchemaDatabase;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationRepositoryIT {
    SchemaDatabase database;
    static final Instant CREATED=Instant.parse("2026-10-14T01:02:03.123456Z");
    static final Instant CANCELLED=Instant.parse("2026-10-14T02:03:04.654321Z");
    @BeforeAll void open() {database=new SchemaDatabase();}
    @AfterAll void close() {if(database!=null)database.close();}
    @BeforeEach void reset() {database.reset();}
    @Test void readsAllSevenColumnsByExactUserAndKeyWithCurrentStatus() {
        var resource=UUID.randomUUID();var userA=UUID.randomUUID();var userB=UUID.randomUUID();
        database.jdbc().update("INSERT INTO resources(id,name,capacity,available,created_at) VALUES (?,?,?,?,?)",resource,"대상",10,8,CREATED.atOffset(ZoneOffset.UTC));
        var a=UUID.randomUUID();var b=UUID.randomUUID();var lower=UUID.randomUUID();
        insert(a,resource,userA,"Key_1","CONFIRMED",null);insert(b,resource,userB,"Key_1","CANCELLED",CANCELLED);insert(lower,resource,userA,"key_1","CANCELLED",CANCELLED);
        var before=database.snapshot();var repository=new ReservationRepository(new NamedParameterJdbcTemplate(database.dataSource()));
        assertThat(repository.findByUserAndKey(userA,"Key_1")).contains(new StoredReservation(a,resource,userA,"Key_1",ReservationStatus.CONFIRMED,CREATED,null));
        assertThat(repository.findByUserAndKey(userB,"Key_1")).contains(new StoredReservation(b,resource,userB,"Key_1",ReservationStatus.CANCELLED,CREATED,CANCELLED));
        assertThat(repository.findByUserAndKey(userA,"key_1")).contains(new StoredReservation(lower,resource,userA,"key_1",ReservationStatus.CANCELLED,CREATED,CANCELLED));
        assertThat(repository.findByUserAndKey(userA,"Key_1 ")).isEmpty();assertThat(repository.findByUserAndKey(userA,"Key_2")).isEmpty();
        assertThat(repository.findByUserAndKey(UUID.randomUUID(),"Key_1")).isEmpty();assertThat(database.snapshot()).isEqualTo(before);
    }
    @Test void propagatesSqlErrors() {
        database.jdbc().execute("ALTER TABLE reservations RENAME COLUMN status TO broken_status");
        try {
            var repository=new ReservationRepository(new NamedParameterJdbcTemplate(database.dataSource()));
            assertThatThrownBy(()->repository.findByUserAndKey(UUID.randomUUID(),"key")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {database.jdbc().execute("ALTER TABLE reservations RENAME COLUMN broken_status TO status");}
    }
    void insert(UUID id,UUID resource,UUID user,String key,String status,Instant cancelled) {
        database.jdbc().update("INSERT INTO reservations(id,resource_id,user_id,idempotency_key,status,created_at,cancelled_at) VALUES (?,?,?,?,?,?,?)",id,resource,user,key,status,CREATED.atOffset(ZoneOffset.UTC),cancelled==null?null:cancelled.atOffset(ZoneOffset.UTC));
    }
}
