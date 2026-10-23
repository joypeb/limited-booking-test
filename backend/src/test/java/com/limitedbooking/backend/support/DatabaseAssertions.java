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

    public record ReservationSnapshot(InventorySnapshot inventory,
            java.util.List<com.limitedbooking.backend.reservation.StoredReservation> reservations) {
        public ReservationSnapshot {reservations=java.util.List.copyOf(reservations);}
    }
    /** 수량과 불변 예약 컬럼을 한 SELECT의 동일 관측 시점에서 읽는다. */
    public ReservationSnapshot reservationSnapshot(UUID resourceId) {
        return jdbc.query("""
                SELECT r.capacity, r.available, v.id, v.resource_id, v.user_id, v.idempotency_key,
                       v.status, v.created_at, v.cancelled_at
                FROM resources r LEFT JOIN reservations v ON v.resource_id = r.id
                WHERE r.id = :resourceId
                """,Map.of("resourceId",resourceId),(org.springframework.jdbc.core.ResultSetExtractor<ReservationSnapshot>) rs->{
            if(!rs.next()) throw new AssertionError("대상 없음 resourceId="+resourceId);
            int capacity=rs.getInt("capacity"),available=rs.getInt("available");
            var reservations=new java.util.ArrayList<com.limitedbooking.backend.reservation.StoredReservation>();
            do {
                UUID id=rs.getObject("id",UUID.class);
                if(id==null)continue;
                var cancelled=rs.getObject("cancelled_at",java.time.OffsetDateTime.class);
                reservations.add(new com.limitedbooking.backend.reservation.StoredReservation(id,
                        rs.getObject("resource_id",UUID.class),rs.getObject("user_id",UUID.class),rs.getString("idempotency_key"),
                        com.limitedbooking.backend.reservation.ReservationStatus.valueOf(rs.getString("status")),
                        rs.getObject("created_at",java.time.OffsetDateTime.class).toInstant(),cancelled==null?null:cancelled.toInstant()));
            } while(rs.next());
            long confirmed=reservations.stream().filter(r->r.status()==com.limitedbooking.backend.reservation.ReservationStatus.CONFIRMED).count();
            if(available<0 || available>capacity || capacity>1000 || available+confirmed!=capacity)
                throw new AssertionError("수량 보존 위반 resourceId="+resourceId);
            return new ReservationSnapshot(new InventorySnapshot(resourceId,capacity,available,reservations.size(),confirmed),reservations);
        });
    }

    private record Inventory(int capacity, int available, long confirmedCount) {}
}
