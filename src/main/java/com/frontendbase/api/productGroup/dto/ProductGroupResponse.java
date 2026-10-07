package com.frontendbase.api.productGroup.dto;

import java.util.*;
import java.time.Instant;
public record ProductGroupResponse(UUID id, String code, String name, String description, Integer status, Instant createdAt, Instant updatedAt) {}
