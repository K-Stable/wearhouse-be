package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.OrderStatus;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "order_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Column(name = "event_id", length = 26)
    private String eventId;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    private OrderStatusHistoryEntity(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String eventId,
            String changedBy,
            String reasonCode,
            LocalDateTime changedAt
    ) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.eventId = eventId;
        this.changedBy = changedBy;
        this.reasonCode = reasonCode;
        this.changedAt = changedAt;
    }

    public static OrderStatusHistoryEntity of(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String eventId,
            String reasonCode
    ) {
        return new OrderStatusHistoryEntity(
                order,
                fromStatus,
                toStatus,
                eventId,
                "SYSTEM",
                reasonCode,
                LocalDateTime.now()
        );
    }
}
