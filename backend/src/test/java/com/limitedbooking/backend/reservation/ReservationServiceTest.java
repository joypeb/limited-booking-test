package com.limitedbooking.backend.reservation;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 조정 분기 전용 대역이며 실제 DB/HTTP·롤백의 증거는 IT에 둔다. */
class ReservationServiceTest {
    final UUID user = UUID.randomUUID(), resource = UUID.randomUUID();
    final IdempotencyKey key = IdempotencyKey.parse("Case_Key");
    final ReservationRepository repository = mock(ReservationRepository.class);
    final ReservationWriter writer = mock(ReservationWriter.class);
    final ReservationService service = new ReservationService(writer, repository);
    StoredReservation stored(UUID id) {
        return new StoredReservation(UUID.randomUUID(),id,user,"Case_Key",ReservationStatus.CANCELLED,
                Instant.parse("2026-10-19T00:00:00.123456Z"),Instant.parse("2026-10-19T01:00:00Z"));
    }
    @Test void replaysSameUuidValueBeforeWriter() {
        var row=stored(UUID.fromString(resource.toString()));
        when(repository.findByUserAndKey(user,"Case_Key")).thenReturn(Optional.of(row));
        var result=service.create(user,resource,key);
        assertThat(result.replayed()).isTrue();
        assertThat(result.body()).isEqualTo(new CreatedReservationView(row.id(),resource,ReservationStatus.CONFIRMED,row.createdAt()));
        verifyNoInteractions(writer);
    }
    @Test void rejectsDifferentResourceBeforeWriter() {
        when(repository.findByUserAndKey(user,"Case_Key")).thenReturn(Optional.of(stored(UUID.randomUUID())));
        assertThatThrownBy(() -> service.create(user,resource,key)).isInstanceOf(ReservationService.IdempotencyConflictException.class);
        verifyNoInteractions(writer);
    }
    @Test void missingKeyCallsWriterAfterLookup() {
        when(repository.findByUserAndKey(user,"Case_Key")).thenReturn(Optional.empty());
        var result=new CreateReservationResult(CreatedReservationView.from(stored(resource)),false);
        when(writer.create(user,resource,key)).thenReturn(result);
        assertThat(service.create(user,resource,key)).isSameAs(result);
        var order=inOrder(repository,writer);
        order.verify(repository).findByUserAndKey(user,"Case_Key");
        order.verify(writer).create(user,resource,key);
        order.verifyNoMoreInteractions();
    }
}
