package com.frontendbase.api.product.service;

import java.util.*;
import com.frontendbase.api.productGroup.repository.ProductGroupRepository;
import com.frontendbase.api.product.entity.Product;
import com.frontendbase.api.product.dto.*;
import com.frontendbase.api.product.mapper.ProductMapper;
import com.frontendbase.api.product.repository.ProductRepository;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final ProductGroupRepository groups;

    @Transactional(readOnly = true)
    public ProductPageResponse search(int page, int pageSize, String keyword, Integer status, UUID groupId) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        var spec = ProductSpecifications.matches(keyword, status);
        if (groupId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("groupId"), groupId));
        var result = repository.findAll(spec, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new ProductPageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), page, pageSize);
    }
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        
        return mapper.toResponse(find(id));
    }
    @Transactional
    public ProductResponse create(ProductPayload payload) {
        Product e = new Product();
        e.setId(UUID.randomUUID());
        ensureUnique(payload.code(), e.getId());
        ensureGroup(payload.groupId(), null);
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        
        return mapper.toResponse(e);
    }
    @Transactional
    public ProductResponse update(UUID id, ProductPayload payload) {
        
        Product e = find(id);
        ensureGroup(payload.groupId(), e.getGroupId());
        ensureUnique(payload.code(), id);
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        return mapper.toResponse(e);
    }
    private Product find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND", "Không tìm thấy dữ liệu"));
    }
    private void ensureUnique(String code, UUID id) {
        if (repository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id))
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_CODE_EXISTS", "Mã đã tồn tại");
    }

    private void ensureGroup(UUID groupId, UUID oldGroupId) {
        var group = groups.findById(groupId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PRODUCT_GROUP_NOT_FOUND", "Không tìm thấy nhóm sản phẩm"));
        if (group.getStatus() != 1 && !groupId.equals(oldGroupId))
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_GROUP_INACTIVE", "Nhóm sản phẩm đã ngừng hoạt động");
    }
}
