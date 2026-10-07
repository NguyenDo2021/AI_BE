package com.frontendbase.api.warehouse.dto;

import java.util.*;
public record WarehousePageResponse(List<WarehouseResponse> items, long total, int page, int pageSize) {}
