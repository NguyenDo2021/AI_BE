package com.frontendbase.api.role.service;

import com.frontendbase.api.role.dto.RoleResponse;
import com.frontendbase.api.role.repository.RoleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(role -> new RoleResponse(
                        role.getId(),
                        role.getName(),
                        role.getCode(),
                        role.getDescription()
                ))
                .toList();
    }
}
