package com.limitedbooking.backend.resource;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public record CreateResourceRequest(String name, Integer capacity) {}

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResourceView> create(@RequestBody CreateResourceRequest request) {
        if (request == null || request.name() == null || request.capacity() == null) {
            throw new IllegalArgumentException("예약 대상 이름과 최초 수량은 필수입니다.");
        }
        ResourceView created = resourceService.create(request.name(), request.capacity());
        return ResponseEntity
                .created(URI.create("/api/resources/" + created.id()))
                .body(created);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResourceView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.get(id));
    }
}
