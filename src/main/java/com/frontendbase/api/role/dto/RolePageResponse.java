package com.frontendbase.api.role.dto;

import java.util.List;

public record RolePageResponse(List<RoleResponse> items, long total, int page, int pageSize) {
}