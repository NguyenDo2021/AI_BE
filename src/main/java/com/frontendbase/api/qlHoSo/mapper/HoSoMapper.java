package com.frontendbase.api.qlHoSo.mapper;

import java.util.Locale;
import java.util.UUID;

import com.frontendbase.api.qlHoSo.dto.HoSoPayload;
import com.frontendbase.api.qlHoSo.dto.HoSoResponse;
import com.frontendbase.api.qlHoSo.entity.HoSo;

public class HoSoMapper {
    public HoSo toNewEntity(HoSoPayload payload) {
        HoSo hoSo = new HoSo();
        hoSo.setId(UUID.randomUUID());
        apply(payload, hoSo);
        return hoSo;
    }

    public void updateEntity(HoSoPayload payload, HoSo hoSo) {
        apply(payload, hoSo);
    }

    public HoSoResponse toResponse(HoSo hoSo) {
        return new HoSoResponse(
                hoSo.getId(),
                hoSo.getUsername(),
                hoSo.getFullName(),
                hoSo.getEmail(),
                hoSo.getPhone(),
                hoSo.getStatus(),
                hoSo.getCreatedAt(),
                hoSo.getUpdatedBy(),
                hoSo.getUpdatedAt());
    }

    private void apply(HoSoPayload payload, HoSo hoSo) {
        hoSo.setUsername(payload.username().trim());
        hoSo.setFullName(payload.fullName().trim());
        hoSo.setEmail(payload.email().trim().toLowerCase(Locale.ROOT));
        hoSo.setPhone(payload.phone() == null || payload.phone().isBlank() ? null : payload.phone().trim());
        hoSo.setStatus(payload.status() == null ? (short) 1 : payload.status().shortValue());
    }
}
