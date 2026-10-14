package com.limitedbooking.backend.resource;
import java.util.UUID;
public record ResourceView(UUID id, String name, int capacity, int available) {}
