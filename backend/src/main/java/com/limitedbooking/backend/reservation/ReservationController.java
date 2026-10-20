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
    @org.springframework.web.bind.annotation.InitBinder
    void strictReservationId(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(java.util.UUID.class, new java.beans.PropertyEditorSupport() {
            @Override public void setAsText(String value) {
                if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    throw new IllegalArgumentException("예약 식별자는 UUID여야 합니다.");
                setValue(java.util.UUID.fromString(value));
            }
        });
    }

    @org.springframework.web.bind.annotation.GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReservationView get(Authentication authentication,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return service.get(identity.userId(authentication), id);
    }

    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReservationView cancel(Authentication authentication,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id,
            jakarta.servlet.http.HttpServletRequest request) {
        // MVC가 디코딩해 매칭한 모든 경로에서 검사한다. raw URI 필터만으로는 인코딩 경로를 놓친다.
        if (com.limitedbooking.backend.common.RequestBodyLimitFilter.getCachedBody(request).length != 0) {
            throw new IllegalArgumentException("예약 취소 요청 본문은 비어 있어야 합니다.");
        }
        return service.cancel(identity.userId(authentication), id);
    }

}
