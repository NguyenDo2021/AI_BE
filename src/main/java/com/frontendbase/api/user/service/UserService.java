package com.frontendbase.api.user.service;

import com.frontendbase.api.common.exception.ApiException;
import com.frontendbase.api.user.dto.UserPageResponse;
import com.frontendbase.api.user.dto.UserPayload;
import com.frontendbase.api.user.dto.UserResponse;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.mapper.UserMapper;
import com.frontendbase.api.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserPageResponse findUsers(int page, int pageSize, String keyword, Integer status) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Số trang hoặc kích thước trang không hợp lệ");
        }
        Page<UserAccount> result = userRepository.findAll(
                UserSpecifications.matches(keyword, status),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        var users = result.getContent();
        Map<UUID, String> updaterNames = resolveUpdaterNames(users);
        return new UserPageResponse(
            users.stream()
                .map(user -> userMapper.toResponse(user, updaterNames.get(user.getUpdatedBy())))
                .toList(),
                result.getTotalElements(),
                page,
                pageSize);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional
    public UserResponse createUser(UserPayload payload) {
        ensureUnique(payload, null);
        UserAccount user = userMapper.toNewEntity(payload);
        user.setPasswordHash(passwordEncoder.encode("Abc@12345"));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserPayload payload) {
        UserAccount user = findUser(id);
        ensureUnique(payload, id);
        userMapper.updateEntity(payload, user);
        user.setUpdatedBy(currentUserId());
        user.setUpdatedAt(Instant.now());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        UserAccount user = findUser(id);
        userRepository.delete(user);
    }

    private UserAccount findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String userId)) {
            return null;
        }
        return UUID.fromString(userId);
    }

    private UserResponse toResponse(UserAccount user) {
        String updaterName = user.getUpdatedBy() == null
                ? null
                : userRepository.findById(user.getUpdatedBy()).map(UserAccount::getFullName).orElse(null);
        return userMapper.toResponse(user, updaterName);
    }

    private Map<UUID, String> resolveUpdaterNames(java.util.List<UserAccount> users) {
        Set<UUID> updaterIds = users.stream()
                .map(UserAccount::getUpdatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (updaterIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(updaterIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName, (first, ignored) -> first));
    }

    private void ensureUnique(UserPayload payload, UUID currentId) {
        boolean duplicateUsername = userRepository.existsByUsernameIgnoreCase(payload.username().trim())
                && (currentId == null || userRepository.findByUsernameIgnoreCase(payload.username().trim())
                        .map(user -> !user.getId().equals(currentId)).orElse(false));
        if (duplicateUsername) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Tên đăng nhập đã tồn tại");
        }
        boolean duplicateEmail = userRepository.existsByEmailIgnoreCase(payload.email().trim())
                && (currentId == null || userRepository.findByEmailIgnoreCase(payload.email().trim())
                        .map(user -> !user.getId().equals(currentId)).orElse(false));
        if (duplicateEmail) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng");
        }
    }
}
