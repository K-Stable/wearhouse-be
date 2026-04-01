package com.wearhouse.order.domain.entity;

import com.wearhouse.order.domain.model.OrderSagaState;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "order_saga_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSagaHistoryEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 40)
    private OrderSagaState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 40)
    private OrderSagaState toState;

    @Column(name = "event_id", length = 26)
    private String eventId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    private OrderSagaHistoryEntity(
            OrderEntity order,
            OrderSagaState fromState,
            OrderSagaState toState,
            String eventId,
            String reasonCode,
            LocalDateTime changedAt
    ) {
        this.order = order;
        this.fromState = fromState;
        this.toState = toState;
        this.eventId = eventId;
        this.reasonCode = reasonCode;
        this.changedAt = changedAt;
    }

    public static OrderSagaHistoryEntity of(
            OrderEntity order,
            OrderSagaState fromState,
            OrderSagaState toState,
            String eventId,
            String reasonCode
    ) {
        return new OrderSagaHistoryEntity(order, fromState, toState, eventId, reasonCode, LocalDateTime.now());
    }
}
