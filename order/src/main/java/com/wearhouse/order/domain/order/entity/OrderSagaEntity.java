package com.wearhouse.order.domain.order.entity;

import com.wearhouse.order.domain.order.model.OrderSagaState;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_saga")
public class OrderSagaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private OrderEntity order;

    @Column(name = "saga_id", nullable = false, unique = true, length = 26)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 40)
    private OrderSagaState state;

    @Column(name = "last_event_id", length = 26)
    private String lastEventId;

    @Column(name = "last_event_type", length = 100)
    private String lastEventType;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "fail_reason_code", length = 50)
    private String failReasonCode;

    protected OrderSagaEntity() {
    }

    private OrderSagaEntity(
            OrderEntity order,
            String sagaId,
            OrderSagaState state,
            String lastEventId,
            String lastEventType,
            LocalDateTime timeoutAt
    ) {
        this.order = order;
        this.sagaId = sagaId;
        this.state = state;
        this.lastEventId = lastEventId;
        this.lastEventType = lastEventType;
        this.timeoutAt = timeoutAt;
        this.retryCount = 0;
    }

    public static OrderSagaEntity create(
            OrderEntity order,
            String sagaId,
            OrderSagaState state,
            String lastEventId,
            String lastEventType,
            LocalDateTime timeoutAt
    ) {
        return new OrderSagaEntity(order, sagaId, state, lastEventId, lastEventType, timeoutAt);
    }

    public void transition(OrderSagaState nextState, String lastEventId, String lastEventType, String failReasonCode) {
        this.state = nextState;
        this.lastEventId = lastEventId;
        this.lastEventType = lastEventType;
        this.failReasonCode = failReasonCode;
    }

    public Long getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public String getSagaId() {
        return sagaId;
    }

    public OrderSagaState getState() {
        return state;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getFailReasonCode() {
        return failReasonCode;
    }
}
