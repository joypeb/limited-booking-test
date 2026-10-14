package com.limitedbooking.backend.resource;

import com.limitedbooking.backend.support.SchemaDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.limitedbooking.backend.support.SchemaDatabases;
import com.limitedbooking.backend.support.SchemaDatabase.Mode;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceRepositoryIT {
    SchemaDatabases databases;
    SchemaDatabase database;
    @BeforeAll void open() { databases=new SchemaDatabases(); }
    @AfterAll void close() { if(databases!=null)databases.close(); }
    @ParameterizedTest(name="{0}") @EnumSource(Mode.class)
    void readsFourStoredFieldsAndReturnsEmptyOnlyForMissingId(Mode mode) {
        database=databases.use(mode);
        var id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        database.jdbc().update("INSERT INTO resources(id,name,capacity,available,created_at) VALUES (?,?,?,?,?)",id,"예약 대상",10,8,OffsetDateTime.parse("2026-10-14T01:02:03.123456Z"));
        var before=database.snapshot();var repository=new ResourceRepository(new NamedParameterJdbcTemplate(database.dataSource()));
        assertThat(repository.find(id)).contains(new ResourceView(id,"예약 대상",10,8));
        assertThat(repository.find(UUID.fromString("00000000-0000-0000-0000-000000000002"))).isEmpty();
        assertThat(database.snapshot()).isEqualTo(before);
    }
    @ParameterizedTest(name="{0}") @EnumSource(Mode.class)
    void propagatesSqlErrors(Mode mode) {
        database=databases.use(mode);
        database.jdbc().execute("ALTER TABLE resources RENAME COLUMN name TO broken_name");
        try {
            var repository=new ResourceRepository(new NamedParameterJdbcTemplate(database.dataSource()));
            assertThatThrownBy(()->repository.find(UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {database.jdbc().execute("ALTER TABLE resources RENAME COLUMN broken_name TO name");}
    }
}
