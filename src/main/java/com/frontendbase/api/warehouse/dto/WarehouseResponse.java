package com.frontendbase.api.warehouse.dto;

import java.util.*;
import java.time.Instant;
public record WarehouseResponse(UUID id, String code, String name, String address, String phone, String note, Integer status, Instant createdAt, Instant updatedAt) {}
