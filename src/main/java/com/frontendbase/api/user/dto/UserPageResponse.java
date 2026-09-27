package com.frontendbase.api.user.dto;

import java.util.List;

public record UserPageResponse(
        List<UserResponse> items,
        long total,
        int page,
        int pageSize
) {
}
