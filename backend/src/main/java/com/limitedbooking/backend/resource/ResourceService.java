package com.limitedbooking.backend.resource;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ResourceService {
    private final ResourceRepository resourceRepository;
    private final Clock clock;

    public static final class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(UUID id) {
            super("예약 대상을 찾을 수 없습니다: " + id);
        }
    }

    public ResourceService(ResourceRepository resourceRepository, Clock clock) {
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    public ResourceView create(String name, int capacity) {
        String normalizedName = ResourceRules.normalizeName(name);
        ResourceRules.validateCapacity(capacity);
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return resourceRepository.insert(id, normalizedName, capacity, createdAt);
    }

    public ResourceView get(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("예약 대상 식별자가 필요합니다.");
        }
        return resourceRepository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }
}
