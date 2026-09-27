package com.frontendbase.api.config;

import com.frontendbase.api.role.repository.RoleRepository;
import com.frontendbase.api.user.entity.UserAccount;
import com.frontendbase.api.user.repository.UserRepository;
import java.util.UUID;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class LocalAdminSeeder implements ApplicationRunner {
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final LocalSeedProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalAdminSeeder(
            LocalSeedProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(properties.admin().username())) {
            return;
        }
        var adminRole = roleRepository.findById(ADMIN_ROLE_ID)
                .orElseThrow(() -> new IllegalStateException("Flyway did not seed the ADMIN role"));
        UserAccount admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setUsername(properties.admin().username().trim());
        admin.setPasswordHash(passwordEncoder.encode(properties.admin().password()));
        admin.setFullName(properties.admin().fullName());
        admin.setEmail(properties.admin().email().trim().toLowerCase(Locale.ROOT));
        admin.setStatus((short) 1);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }
}
