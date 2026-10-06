package com.frontendbase.api.qlHoSo.dto;

import java.util.List;

public record HoSoPageResponse(
        List<HoSoResponse> items,
        long total,
        int page,
        int pageSize
) {
}
