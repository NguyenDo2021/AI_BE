package com.frontendbase.api.qlHoSo.dto;

import java.util.List;

public class HoSoPageResponse {
    List<HoSoResponse> items;
    long total;
    int page;
    int pageSize;
}
