package com.frontendbase.api.security.dto;

import java.util.List;

public record PermissionPageResponse(List<PermissionResponse> items, long total, int page, int pageSize) {
}