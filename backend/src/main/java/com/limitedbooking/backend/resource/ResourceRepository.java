package com.limitedbooking.backend.resource;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResourceRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ResourceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int decrementIfAvailable(UUID resourceId) {
        return jdbc.update("""
                UPDATE resources SET available = available - 1
                WHERE id = :resourceId AND available > 0
                """, Map.of("resourceId", resourceId));
    }

    public Optional<ResourceView> find(UUID id) {
        var rows = jdbc.query("""
                SELECT id, name, capacity, available
                FROM resources
                WHERE id = :id
                """, Map.of("id", id), (rs, rowNumber) -> new ResourceView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getInt("capacity"), rs.getInt("available")));
        return Optional.ofNullable(DataAccessUtils.singleResult(rows));
    }

    public ResourceView insert(UUID id, String name, int capacity, Instant createdAt) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("capacity", capacity)
                .addValue("available", capacity)
                .addValue("createdAt", createdAt.atOffset(ZoneOffset.UTC));
        var rows = jdbc.query("""
                INSERT INTO resources (id, name, capacity, available, created_at)
                VALUES (:id, :name, :capacity, :available, :createdAt)
                RETURNING id, name, capacity, available
                """, params, (rs, rowNumber) -> new ResourceView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getInt("capacity"), rs.getInt("available")));
        ResourceView inserted = DataAccessUtils.requiredSingleResult(rows);
        return inserted;
    }
}
