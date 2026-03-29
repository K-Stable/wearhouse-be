package com.wearhouse.order.paymentintegration.mapper;

import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.paymentintegration.dto.response.PaymentPrepareInternalResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentIntegrationResponseMapper {

    public OrderPaymentConfirmResponse toConfirmResponse(
            OrderEntity order,
            OrderStatus status,
            String reasonCode
    ) {
        return new OrderPaymentConfirmResponse(
                order.getId(),
                order.getOrderNo(),
                status.name(),
                reasonCode
        );
    }

    public OrderPaymentPrepareResponse toEmptyPrepareResponse() {
        return new OrderPaymentPrepareResponse(null, null, null, null);
    }

    public OrderPaymentPrepareResponse toPrepareResponse(PaymentPrepareInternalResponse data) {
        return new OrderPaymentPrepareResponse(
                data.checkoutSessionId(),
                data.checkoutUrl(),
                data.appLaunchUrl(),
                data.checkoutExpiresAt()
        );
    }
}
