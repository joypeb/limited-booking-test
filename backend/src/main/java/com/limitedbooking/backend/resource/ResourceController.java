package com.limitedbooking.backend.resource;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.beans.PropertyEditorSupport;
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

    // 기본 변환(StringToUUIDConverter)은 공백 trim과 "1-1-1-1-1" 같은 축약형을 받아들인다.
    // 계약의 uuid 경로는 8-4-4-4-12 원문만 허용하고 나머지는 400으로 거부한다.
    @InitBinder
    void strictResourceId(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
            @Override public void setAsText(String value) {
                if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                    throw new IllegalArgumentException("예약 대상 식별자는 UUID여야 합니다.");
                setValue(UUID.fromString(value));
            }
        });
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResourceView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.get(id));
    }
}
