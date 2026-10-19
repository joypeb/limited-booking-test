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
    RuntimeException duplicate() {
        return new org.springframework.dao.DuplicateKeyException("중복", com.limitedbooking.backend.common.UniqueViolationClassifierTest.pg("23505","uq_reservations_user_key"));
    }
    void duplicateThen(Optional<StoredReservation> after) {
        when(repository.findByUserAndKey(user,"Case_Key")).thenReturn(Optional.empty(),after);
        when(writer.create(user,resource,key)).thenThrow(duplicate());
    }
    void verifyRecoveryOrder() {
        var order=inOrder(repository,writer);
        order.verify(repository).findByUserAndKey(user,"Case_Key");
        order.verify(writer).create(user,resource,key);
        order.verify(repository).findByUserAndKey(user,"Case_Key");
        order.verifyNoMoreInteractions();
    }
    @Test void duplicateAfterWriterReturnsRequeriesAndReplays() {
        var existing=stored(resource);duplicateThen(Optional.of(existing));
        var result=service.create(user,resource,key);
        assertThat(result.replayed()).isTrue();assertThat(result.body().id()).isEqualTo(existing.id());
        verifyRecoveryOrder();
    }
    @Test void duplicateAfterWriterReturnsRequeriesAndConflicts() {
        duplicateThen(Optional.of(stored(UUID.randomUUID())));
        assertThatThrownBy(() -> service.create(user,resource,key)).isInstanceOf(ReservationService.IdempotencyConflictException.class);
        verifyRecoveryOrder();
    }
    @Test void duplicateWithoutCommittedSuccessIsInternalDefect() {
        duplicateThen(Optional.empty());
        assertThatThrownBy(() -> service.create(user,resource,key)).isInstanceOf(IllegalStateException.class);
        verifyRecoveryOrder();
    }
    @Test void nonTargetFailuresPropagateWithoutRetry() {
        for (RuntimeException failure : List.of(new org.springframework.dao.DuplicateKeyException("PK",
                com.limitedbooking.backend.common.UniqueViolationClassifierTest.pg("23505","reservations_pkey")),
                new org.springframework.dao.DataIntegrityViolationException("FK",
                com.limitedbooking.backend.common.UniqueViolationClassifierTest.pg("23503","fk_reservations_resource")),
                new org.springframework.dao.DataAccessResourceFailureException("연결"),new IllegalStateException("내부"))) {
            reset(repository,writer);
            when(writer.create(user,resource,key)).thenThrow(failure);
            assertThatThrownBy(() -> service.create(user,resource,key)).isSameAs(failure);
            var order=inOrder(repository,writer);
            order.verify(repository).findByUserAndKey(user,"Case_Key");
            order.verify(writer).create(user,resource,key);order.verifyNoMoreInteractions();
        }
    }

}
