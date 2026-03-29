package com.wearhouse.user.domain.entity;

import com.wearhouse.user.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

    @Column(name = "seller_no", nullable = false, length = 100)
    private String sellerNo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "user_version", nullable = false)
    private Long userVersion;

    private SellerEntity(
            String loginId,
            String password,
            String email,
            String name,
            String phone,
            String sellerNo,
            String status,
            Long userVersion
    ) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.sellerNo = sellerNo;
        this.status = status;
            this.userVersion = userVersion;
    }

    public static SellerEntity of(
            String loginId,
            String email,
            String encodedPassword,
            String name,
            String phone,
            String sellerNo
    ) {
        return new SellerEntity(
                loginId,
                encodedPassword,
                email,
                name,
                phone,
                sellerNo,
                "ACTIVE",
                1L
        );
    }

    public static SellerEntity of(String email, String encodedPassword, String name) {
        return new SellerEntity(
                email,
                encodedPassword,
                email,
                name,
                "",
                "",
                "ACTIVE",
                1L
        );
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.userVersion = this.userVersion + 1;
    }
}
