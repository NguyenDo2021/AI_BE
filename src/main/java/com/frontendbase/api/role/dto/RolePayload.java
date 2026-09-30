package com.frontendbase.api.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RolePayload(
        @NotBlank(message = "Tên vai trò không được để trống") @Size(max = 100, message = "Tên vai trò tối đa 100 ký tự") String name,
        @NotBlank(message = "Mã vai trò không được để trống") @Size(max = 80, message = "Mã vai trò tối đa 80 ký tự") @Pattern(regexp = "(?i)^[A-Z][A-Z0-9_]*$", message = "Mã vai trò chỉ gồm chữ cái, số và dấu gạch dưới") String code,
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description,
        @NotNull(message = "Trạng thái là bắt buộc") @Min(value = 0, message = "Trạng thái chỉ nhận 0 hoặc 1") @Max(value = 1, message = "Trạng thái chỉ nhận 0 hoặc 1") Integer status) {
}