package com.frontendbase.api.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionPayload(
        @NotBlank(message = "Tên quyền không được để trống") @Size(max = 255, message = "Tên quyền tối đa 255 ký tự") String name,
        @NotBlank(message = "Mã quyền không được để trống") @Size(max = 100, message = "Mã quyền tối đa 100 ký tự") @Pattern(regexp = "(?i)^[A-Z][A-Z0-9_]*$", message = "Mã quyền chỉ gồm chữ cái, số và dấu gạch dưới") String code,
        @NotBlank(message = "Mô tả không được để trống") @Size(max = 255, message = "Mô tả tối đa 255 ký tự") String description,
        @NotNull(message = "Trạng thái là bắt buộc") @Min(value = 0, message = "Trạng thái chỉ nhận 0 hoặc 1") @Max(value = 1, message = "Trạng thái chỉ nhận 0 hoặc 1") Integer status) {
}