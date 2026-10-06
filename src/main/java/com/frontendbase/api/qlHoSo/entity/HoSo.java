package com.frontendbase.api.qlHoSo.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ho_so")
@Getter
@Setter
@NoArgsConstructor
public class HoSo {
    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 80, unique = true)
    private String usernameNormalized;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254, unique = true)
    private String emailNormalized;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "smallint")
    private short status = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<HoSo> roles = new HashSet<>();

    @PrePersist
    @PreUpdate
    private void updateNormalizedFields() {
        usernameNormalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        emailNormalized = email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
