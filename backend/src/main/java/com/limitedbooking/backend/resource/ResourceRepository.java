package com.limitedbooking.backend.resource;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResourceRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ResourceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
