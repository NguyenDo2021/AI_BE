package com.frontendbase.api.warehouse.service;

import java.util.*;
import com.frontendbase.api.warehouse.service.WarehouseAccess;
import com.frontendbase.api.warehouse.repository.UserWarehouseRepository;
import com.frontendbase.api.warehouse.entity.UserWarehouse;
import com.frontendbase.api.warehouse.entity.Warehouse;
import com.frontendbase.api.warehouse.dto.*;
import com.frontendbase.api.warehouse.mapper.WarehouseMapper;
import com.frontendbase.api.warehouse.repository.WarehouseRepository;
import com.frontendbase.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository repository;
    private final WarehouseMapper mapper;
    private final WarehouseAccess access;
    private final UserWarehouseRepository assignments;

    @Transactional(readOnly = true)
    public WarehousePageResponse search(int page, int pageSize, String keyword, Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        var spec = WarehouseSpecifications.matches(keyword, status);
        spec = spec.and(access.visibleWarehouses());
        var result = repository.findAll(spec, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new WarehousePageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getTotalElements(), page, pageSize);
    }
    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        access.requireWarehouse(id);
        return mapper.toResponse(find(id));
    }
    @Transactional
    public WarehouseResponse create(WarehousePayload payload) {
        Warehouse e = new Warehouse();
        e.setId(UUID.randomUUID());
        ensureUnique(payload.code(), e.getId());
        
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        if (!access.isAdmin()) assignments.save(new UserWarehouse(access.currentUserId(), e.getId()));
        return mapper.toResponse(e);
    }
    @Transactional
    public WarehouseResponse update(UUID id, WarehousePayload payload) {
        access.requireWarehouse(id);
        Warehouse e = find(id);
        
        ensureUnique(payload.code(), id);
        mapper.apply(payload, e);
        e = repository.saveAndFlush(e);
        return mapper.toResponse(e);
    }
    private Warehouse find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "WAREHOUSE_NOT_FOUND", "Không tìm thấy dữ liệu"));
    }
    private void ensureUnique(String code, UUID id) {
        if (repository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id))
            throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_CODE_EXISTS", "Mã đã tồn tại");
    }
}
