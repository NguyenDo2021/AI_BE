package com.frontendbase.api.user.mapper;

import com.frontendbase.api.user.dto.UserPayload;
import com.frontendbase.api.user.dto.UserResponse;
import com.frontendbase.api.user.entity.UserAccount;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserAccount toNewEntity(UserPayload payload) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        apply(payload, user);
        return user;
    }

    public void updateEntity(UserPayload payload, UserAccount user) {
        apply(payload, user);
    }

    public UserResponse toResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private void apply(UserPayload payload, UserAccount user) {
        user.setUsername(payload.username().trim());
        user.setFullName(payload.fullName().trim());
        user.setEmail(payload.email().trim().toLowerCase(Locale.ROOT));
        user.setPhone(payload.phone() == null || payload.phone().isBlank() ? null : payload.phone().trim());
        user.setStatus(payload.status() == null ? (short) 1 : payload.status().shortValue());
    }
}
