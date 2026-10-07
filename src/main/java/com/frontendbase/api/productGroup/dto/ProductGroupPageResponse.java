package com.frontendbase.api.productGroup.dto;

import java.util.*;
public record ProductGroupPageResponse(List<ProductGroupResponse> items, long total, int page, int pageSize) {}
