package com.limitedbooking.backend.support;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

/** 한 SQL 문장의 동일 관측 시점에서 수량 보존을 검사한다. */
public final class DatabaseAssertions {
    private final NamedParameterJdbcTemplate jdbc;

    public DatabaseAssertions(DataSource dataSource) {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public void assertInventoryConserved(UUID resourceId) {
        var rows = jdbc.query("""
                SELECT r.capacity, r.available, count(reservation.id) AS confirmed_count
                FROM resources r
                LEFT JOIN reservations reservation
                    ON reservation.resource_id = r.id AND reservation.status = 'CONFIRMED'
                WHERE r.id = :resourceId
                GROUP BY r.id, r.capacity, r.available
                """, Map.of("resourceId", resourceId), (rs, rowNumber) -> new Inventory(
                rs.getInt("capacity"), rs.getInt("available"), rs.getLong("confirmed_count")));
        if (rows.size() != 1) {
            throw new AssertionError("대상 없음 resourceId=" + resourceId);
        }
        var inventory = rows.getFirst();
        if (inventory.available() < 0 || inventory.available() > inventory.capacity()
                || inventory.available() + inventory.confirmedCount() != inventory.capacity()) {
            throw new AssertionError("수량 보존 위반 resourceId=" + resourceId
                    + " capacity=" + inventory.capacity() + " available=" + inventory.available()
                    + " confirmedCount=" + inventory.confirmedCount());
        }
    }

    public record InventorySnapshot(UUID resourceId, int capacity, int available, long totalCount, long confirmedCount) {}
    public Map<UUID, InventorySnapshot> snapshot(java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) throw new IllegalArgumentException("빈 대상 목록");
        var requested = java.util.Set.copyOf(ids);
        var rows = jdbc.query("""
                SELECT r.id, r.capacity, r.available, count(v.id) AS total_count,
                       count(v.id) FILTER (WHERE v.status = 'CONFIRMED') AS confirmed_count
                FROM resources r LEFT JOIN reservations v ON v.resource_id = r.id
                WHERE r.id IN (:ids)
                GROUP BY r.id, r.capacity, r.available
                """, Map.of("ids",requested), (rs,n) -> new InventorySnapshot(
                rs.getObject("id",UUID.class),rs.getInt("capacity"),rs.getInt("available"),
                rs.getLong("total_count"),rs.getLong("confirmed_count")));
        var result = new java.util.LinkedHashMap<UUID,InventorySnapshot>();
        for (var row : rows) {
            if (row.available()<0 || row.available()>row.capacity() || row.capacity()>1000
                    || row.available()+row.confirmedCount()!=row.capacity())
                throw new AssertionError("수량 보존 위반 " + row);
            result.put(row.resourceId(),row);
        }
        if (!result.keySet().equals(requested)) {
            var missing = new java.util.HashSet<>(requested); missing.removeAll(result.keySet());
            throw new AssertionError("대상 없음 " + missing);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private record Inventory(int capacity, int available, long confirmedCount) {}
}
