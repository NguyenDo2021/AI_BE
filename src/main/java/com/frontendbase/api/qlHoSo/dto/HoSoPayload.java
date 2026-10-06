package com.frontendbase.api.qlHoSo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HoSoPayload(@NotBlank @Size(max = 80) String username,

        @NotBlank @Size(max = 160) String fullName,

        @NotBlank @Email @Size(max = 254) String email,

        @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Số điện thoại không hợp lệ") String phone,

        @Min(0) @Max(1) Integer status) {

}
