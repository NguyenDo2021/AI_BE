package com.frontendbase.api.warehouse.mapper;

import java.util.*;
import org.springframework.stereotype.Component;
import com.frontendbase.api.warehouse.entity.Warehouse;
import com.frontendbase.api.warehouse.dto.*;
@Component
public class WarehouseMapper {
    public void apply(WarehousePayload p, Warehouse e) {
        e.setCode(p.code().trim().toUpperCase(Locale.ROOT));
        e.setName(p.name().trim());
        e.setAddress(p.address() == null || p.address().isBlank() ? null : p.address().trim());
        e.setPhone(p.phone() == null || p.phone().isBlank() ? null : p.phone().trim());
        e.setNote(p.note() == null || p.note().isBlank() ? null : p.note().trim());
        e.setStatus(p.status().shortValue());
    }
    public WarehouseResponse toResponse(Warehouse e) {
        return new WarehouseResponse(e.getId(), e.getCode(), e.getName(), e.getAddress(), e.getPhone(), e.getNote(), (int) e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
