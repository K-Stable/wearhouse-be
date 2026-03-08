package com.wearhouse.user.domain.entity;

import com.wearhouse.user.infra.jpa.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "seller")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "user_version", nullable = false)
    private Long userVersion;

    @Builder
    private SellerEntity(String email, String password, String name, String status, Long userVersion) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.status = status;
        this.userVersion = userVersion;
    }

    public static SellerEntity create(String email, String password, String name) {
        return SellerEntity.builder()
                .email(email)
                .password(password)
                .name(name)
                .status("ACTIVE")
                .userVersion(1L)
                .build();
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
