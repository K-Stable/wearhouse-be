package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.OrderItemStatus;
import com.wearhouse.order.infra.jpa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "option_name_snapshot", length = 200)
    private String optionNameSnapshot;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "line_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderItemStatus status;

    private OrderItemEntity(
            OrderEntity order,
            Long productId,
            Long optionId,
            Long sellerId,
            String productNameSnapshot,
            String optionNameSnapshot,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineAmount,
            OrderItemStatus status
    ) {
        this.order = order;
        this.productId = productId;
        this.optionId = optionId;
        this.sellerId = sellerId;
        this.productNameSnapshot = productNameSnapshot;
        this.optionNameSnapshot = optionNameSnapshot;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = lineAmount;
        this.status = status;
    }

    public static OrderItemEntity of(
            OrderEntity order,
            Long productId,
            Long optionId,
            Long sellerId,
            String productNameSnapshot,
            String optionNameSnapshot,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineAmount,
            OrderItemStatus status
    ) {
        return new OrderItemEntity(
                order,
                productId,
                optionId,
                sellerId,
                productNameSnapshot,
                optionNameSnapshot,
                unitPrice,
                quantity,
                lineAmount,
                status
        );
    }

    public void updateStatus(OrderItemStatus status) {
        this.status = status;
    }
}
