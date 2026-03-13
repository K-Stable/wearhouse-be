package com.wearhouse.user.domain.entity;

import com.wearhouse.user.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "buyer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuyerEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 255)
    private String loginId;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "point", nullable = false, length = 30)
    private String point;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "user_version", nullable = false)
    private Long userVersion;

    @Builder
    private BuyerEntity(
            String loginId,
            String password,
            String email,
            String name,
            String phone,
            String point,
            String status,
            Long userVersion
    ) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.point = point == null ? "0" : point;
        this.status = status;
        this.userVersion = userVersion;
    }

    public static BuyerEntity create(String loginId, String email, String encodedPassword, String name, String phone) {
        return BuyerEntity.builder()
                .loginId(loginId)
                .password(encodedPassword)
                .email(email)
                .name(name)
                .phone(phone)
                .point("0")
                .status("ACTIVE")
                .userVersion(1L)
                .build();
    }

    public static BuyerEntity create(String email, String encodedPassword, String name) {
        return BuyerEntity.builder()
                .loginId(email)
                .password(encodedPassword)
                .email(email)
                .name(name)
                .phone("")
                .point("0")
                .status("ACTIVE")
                .userVersion(1L)
                .build();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.userVersion = this.userVersion + 1;
    }
}
