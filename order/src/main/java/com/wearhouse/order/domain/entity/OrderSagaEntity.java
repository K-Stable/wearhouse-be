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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "order_saga")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "fail_reason_code", length = 50)
    private String failReasonCode;

    private OrderSagaEntity(
            OrderEntity order,
            String sagaId,
            OrderSagaState state,
            String lastEventId
    ) {
        this.order = order;
        this.sagaId = sagaId;
        this.state = state;
        this.lastEventId = lastEventId;
    }

    public static OrderSagaEntity of(
            OrderEntity order,
            String sagaId,
            OrderSagaState state,
            String lastEventId
    ) {
        return new OrderSagaEntity(order, sagaId, state, lastEventId);
    }

    public void transition(OrderSagaState nextState, String lastEventId, String failReasonCode) {
        this.state = nextState;
        this.lastEventId = lastEventId;
        this.failReasonCode = failReasonCode;
    }
}
