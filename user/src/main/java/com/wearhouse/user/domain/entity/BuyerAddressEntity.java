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
@Table(name = "user_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuyerAddressEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "address1", nullable = false, length = 255)
    private String address1;

    @Column(name = "address2", length = 255)
    private String address2;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private BuyerAddressEntity(
            String userType,
            Long userId,
            String label,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2,
            boolean isDefault
    ) {
        this.userType = userType;
        this.userId = userId;
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = isDefault;
    }

    public static BuyerAddressEntity of(
            String userType,
            Long userId,
            String label,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2,
            boolean isDefault
    ) {
        return new BuyerAddressEntity(
                userType,
                userId,
                label,
                recipientName,
                recipientPhone,
                zipCode,
                address1,
                address2,
                isDefault
        );
    }

    public void update(
            String label,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2
    ) {
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public void markDefault(boolean value) {
        this.isDefault = value;
    }
}
