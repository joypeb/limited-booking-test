package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.resource.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SQL 관측 순서 분기만 검사한다. 실제 경쟁·트랜잭션은 대체하지 않는다. */
class ReservationWriterTest {
    final UUID user=UUID.randomUUID(), resource=UUID.randomUUID();
    final IdempotencyKey key=IdempotencyKey.parse("key");
    final ResourceRepository resources=mock(ResourceRepository.class);
    final ReservationRepository reservations=mock(ReservationRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ReservationWriter writer=new ReservationWriter(resources,reservations,jdbc,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
    StoredReservation row(UUID resourceId) {
        return new StoredReservation(UUID.randomUUID(),resourceId,user,"key",ReservationStatus.CONFIRMED,Instant.EPOCH,null);
    }
    @Test void updateZeroRequeriesKeyAndReplaysBeforeResourceLookup() {
        var stored=row(resource);
        when(reservations.findByUserAndKey(user,"key")).thenReturn(Optional.of(stored));
        var result=writer.create(user,resource,key);
        assertThat(result.replayed()).isTrue();
        assertThat(result.body().id()).isEqualTo(stored.id());
        var order=inOrder(resources,reservations);
        order.verify(resources).decrementIfAvailable(resource);
        order.verify(reservations).findByUserAndKey(user,"key");
        order.verifyNoMoreInteractions();
    }
    @Test void updateZeroRequeriesKeyAndConflictsBeforeResourceLookup() {
        when(reservations.findByUserAndKey(user,"key")).thenReturn(Optional.of(row(UUID.randomUUID())));
        assertThatThrownBy(() -> writer.create(user,resource,key)).isInstanceOf(ReservationService.IdempotencyConflictException.class);
        verify(resources,never()).find(any());
        verify(reservations,never()).insert(any(),any(),any(),any(),any());
    }
    @Test void updateZeroWithoutKeyChecksMissingResource() {
        assertThatThrownBy(() -> writer.create(user,resource,key)).isInstanceOf(ResourceService.ResourceNotFoundException.class);
        var order=inOrder(resources,reservations);
        order.verify(resources).decrementIfAvailable(resource);
        order.verify(reservations).findByUserAndKey(user,"key");
        order.verify(resources).find(resource);
    }
    @Test void updateZeroWithoutKeyChecksSoldOutResource() {
        when(resources.find(resource)).thenReturn(Optional.of(new ResourceView(resource,"대상",0,0)));
        assertThatThrownBy(() -> writer.create(user,resource,key)).isInstanceOf(ReservationWriter.SoldOutException.class);
        var order=inOrder(resources,reservations);
        order.verify(resources).decrementIfAvailable(resource);
        order.verify(reservations).findByUserAndKey(user,"key");
        order.verify(resources).find(resource);
    }
}
