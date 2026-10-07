package com.frontendbase.api.warehouse.service;

import java.util.*;
import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.user.repository.UserRepository;
import com.frontendbase.api.warehouse.entity.*;
import com.frontendbase.api.warehouse.repository.*;
import com.frontendbase.api.warehouse.mapper.WarehouseMapper;
import com.frontendbase.api.warehouse.dto.WarehouseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class UserWarehouseService {
    private final UserRepository users;
    private final WarehouseRepository warehouses;
    private final UserWarehouseRepository assignments;
    private final WarehouseAccess access;
    private final WarehouseMapper mapper;
    @Transactional(readOnly = true)
    public List<WarehouseResponse> mine() {
        return warehouses.findAll(access.visibleWarehouses(), Sort.by("code")).stream().map(mapper::toResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(UUID userId) {
        requireUser(userId);
        return warehouses.findAll(access.assignedTo(userId).and(access.visibleWarehouses()), Sort.by("code"))
                .stream().map(mapper::toResponse).toList();
    }
    @Transactional
    public WarehouseResponse assign(UUID userId, UUID warehouseId) {
        access.requireWarehouse(warehouseId);
        // Serialize assignment changes for this user to make repeated PUT requests safe.
        lockUser(userId);
        var warehouse = warehouses.findById(warehouseId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Không tìm thấy kho"));
        if (assignments.existsByUserIdAndWarehouseId(userId, warehouseId)) return mapper.toResponse(warehouse);
        if (warehouse.getStatus() != 1)
            throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_INACTIVE", "Không thể gán kho ngừng hoạt động");
        assignments.saveAndFlush(new UserWarehouse(userId, warehouseId));
        return mapper.toResponse(warehouse);
    }
    @Transactional
    public void remove(UUID userId, UUID warehouseId) {
        access.requireWarehouse(warehouseId);
        lockUser(userId);
        if (!warehouses.existsById(warehouseId))
            throw new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Không tìm thấy kho");
        var key = new UserWarehouseId(userId, warehouseId);
        var assignment = assignments.findById(key).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_WAREHOUSE_NOT_FOUND", "Người dùng chưa được gán kho này"));
        assignments.delete(assignment);
    }
    private void requireUser(UUID id) {
        if (!users.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng");
    }
    private void lockUser(UUID id) {
        users.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
}
