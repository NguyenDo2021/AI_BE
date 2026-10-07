package com.frontendbase.api.productGroup.service;

import java.util.*;
import com.frontendbase.api.productGroup.entity.ProductGroup;
import com.frontendbase.api.productGroup.dto.*;
import com.frontendbase.api.productGroup.mapper.ProductGroupMapper;
import com.frontendbase.api.productGroup.repository.ProductGroupRepository;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class ProductGroupService {
    private final ProductGroupRepository repository;
    private final ProductGroupMapper mapper;

    @Transactional(readOnly = true)
    public ProductGroupPageResponse search(int page, int pageSize, String keyword, Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        var spec = ProductGroupSpecifications.matches(keyword, status);
        
        var result = repository.findAll(spec, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new ProductGroupPageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), page, pageSize);
    }
    @Transactional(readOnly = true)
    public ProductGroupResponse get(UUID id) {
        
        return mapper.toResponse(find(id));
    }
    @Transactional
    public ProductGroupResponse create(ProductGroupPayload payload) {
        ProductGroup e = new ProductGroup();
        e.setId(UUID.randomUUID());
        ensureUnique(payload.code(), e.getId());
        
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        
        return mapper.toResponse(e);
    }
    @Transactional
    public ProductGroupResponse update(UUID id, ProductGroupPayload payload) {
        
        ProductGroup e = find(id);
        
        ensureUnique(payload.code(), id);
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        return mapper.toResponse(e);
    }
    private ProductGroup find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PRODUCT_GROUP_NOT_FOUND", "Không tìm thấy dữ liệu"));
    }
    private void ensureUnique(String code, UUID id) {
        if (repository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id))
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_GROUP_CODE_EXISTS", "Mã đã tồn tại");
    }
}
