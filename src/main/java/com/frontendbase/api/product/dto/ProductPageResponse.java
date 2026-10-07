package com.frontendbase.api.product.dto;

import java.util.*;
public record ProductPageResponse(List<ProductResponse> items, long total, int page, int pageSize) {}
