package com.limitedbooking.backend.reservation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import java.util.regex.Pattern;

public record CreateReservationRequest(UUID resourceId) {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    // 기본 UUID 역직렬화기의 Base64 허용·공백 제거를 HTTP 계약에 적용하지 않는다.
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static CreateReservationRequest fromJson(@JsonProperty("resourceId") String rawResourceId) {
        if (rawResourceId == null || !UUID_TEXT.matcher(rawResourceId).matches()) {
            throw new IllegalArgumentException("예약 대상은 UUID 문자열이어야 합니다.");
        }
        return new CreateReservationRequest(UUID.fromString(rawResourceId));
    }
}
