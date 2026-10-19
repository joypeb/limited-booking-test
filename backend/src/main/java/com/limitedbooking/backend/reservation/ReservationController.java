package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.security.LocalIdentityResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService service;
    private final LocalIdentityResolver identity;
    public ReservationController(ReservationService service, LocalIdentityResolver identity) {
        this.service = service;
        this.identity = identity;
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreatedReservationView> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") String rawKey, @RequestBody CreateReservationRequest request) {
        if (request == null || request.resourceId() == null) throw new IllegalArgumentException("예약 대상은 필수입니다.");
        var result = service.create(identity.userId(authentication), request.resourceId(), IdempotencyKey.parse(rawKey));
        return ResponseEntity.created(URI.create("/api/reservations/" + result.body().id()))
                .header("Idempotent-Replayed", Boolean.toString(result.replayed())).body(result.body());
    }
}
