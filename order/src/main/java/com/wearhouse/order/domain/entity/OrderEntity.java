package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.OrderItemStatus;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "item_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal itemAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "point_used_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal pointUsedAmount;

    @Embedded
    private OrderInfo orderInfo;

    @Column(name = "fail_reason_code", length = 50)
    private String failReasonCode;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    @Builder
    private OrderEntity(
            String orderNo,
            Long buyerId,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            BigDecimal itemAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal pointUsedAmount,
            OrderInfo orderInfo,
            LocalDateTime orderedAt
    ) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.itemAmount = itemAmount;
        this.shippingFee = shippingFee;
        this.discountAmount = discountAmount;
        this.pointUsedAmount = pointUsedAmount;
        this.orderInfo = orderInfo;
        this.orderedAt = orderedAt;
    }

    public static OrderEntity create(
            String orderNo,
            Long buyerId,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            BigDecimal itemAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal pointUsedAmount,
            OrderInfo orderInfo,
            LocalDateTime orderedAt
    ) {
        return new OrderEntity(
                orderNo,
                buyerId,
                status,
                totalAmount,
                currency,
                itemAmount,
                shippingFee,
                discountAmount,
                pointUsedAmount,
                orderInfo,
                orderedAt
        );
    }

    public void addItem(
            Long productId,
            Long optionId,
            Long sellerId,
            String productNameSnapshot,
            String optionNameSnapshot,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineAmount
    ) {
        OrderItemEntity item = OrderItemEntity.create(
                this,
                productId,
                optionId,
                sellerId,
                productNameSnapshot,
                optionNameSnapshot,
                unitPrice,
                quantity,
                lineAmount,
                OrderItemStatus.PENDING_RESERVE
        );
        this.items.add(item);
    }

    public void updateStatus(OrderStatus newStatus, String reasonCode, LocalDateTime confirmedAt, LocalDateTime cancelledAt) {
        this.status = newStatus;
        this.failReasonCode = reasonCode;
        if (confirmedAt != null) {
            this.confirmedAt = confirmedAt;
        }
        if (cancelledAt != null) {
            this.cancelledAt = cancelledAt;
        }
    }

    public void markItemsReserved() {
        for (OrderItemEntity item : items) {
            item.updateStatus(OrderItemStatus.RESERVED);
        }
    }

    public void markItemsConfirmed() {
        for (OrderItemEntity item : items) {
            item.updateStatus(OrderItemStatus.CONFIRMED);
        }
    }

    public void markItemsCancelled() {
        for (OrderItemEntity item : items) {
            item.updateStatus(OrderItemStatus.CANCELLED);
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getItemAmount() {
        return itemAmount;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getPointUsedAmount() {
        return pointUsedAmount;
    }

    public OrderInfo getOrderInfo() {
        return orderInfo;
    }

    public String getFailReasonCode() {
        return failReasonCode;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<OrderItemEntity> getItems() {
        return items;
    }
}
