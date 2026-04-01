package com.wearhouse.order.paymentintegration.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.paymentintegration.client.OrderPaymentIntegrationClient;
import com.wearhouse.order.paymentintegration.dto.request.PaymentConfirmInternalRequest;
import com.wearhouse.order.paymentintegration.dto.request.PaymentPrepareInternalRequest;
import com.wearhouse.order.paymentintegration.dto.response.PaymentConfirmInternalResponse;
import com.wearhouse.order.paymentintegration.dto.response.PaymentPrepareInternalResponse;
import com.wearhouse.order.support.config.OrderInternalProperties;
import com.wearhouse.order.support.config.OrderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentIntegrationGatewayAdapter {

    private final OrderPaymentIntegrationClient orderPaymentIntegrationClient;
    private final OrderInternalProperties orderInternalProperties;
    private final OrderProperties orderProperties;

    public PaymentConfirmInternalResponse requestStableConfirm(OrderEntity order, OrderPaymentConfirmRequest request) {
        ApiResponse<PaymentConfirmInternalResponse> confirmResponse = orderPaymentIntegrationClient.confirmStablepayPayment(
                orderInternalProperties.sharedSecret(),
                new PaymentConfirmInternalRequest(
                        order.getId(),
                        request.orderNo(),
                        request.paymentKey(),
                        request.amount()
                )
        );
        PaymentConfirmInternalResponse data = confirmResponse == null ? null : confirmResponse.data();
        if (data == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 확정 응답이 비어 있습니다.");
        }
        return data;
    }

    public PaymentPrepareInternalResponse requestStablePrepare(
            OrderEntity order,
            String customerId,
            String orderName,
            String idempotencyKey
    ) {
        ApiResponse<PaymentPrepareInternalResponse> prepareResponse = orderPaymentIntegrationClient.prepareStablepayPayment(
                orderInternalProperties.sharedSecret(),
                new PaymentPrepareInternalRequest(
                        order.getId(),
                        order.getOrderNo(),
                        customerId,
                        orderName,
                        order.getTotalAmount(),
                        buildPaymentPrepareSuccessUrl(order.getOrderNo()),
                        buildPaymentPrepareFailUrl(order.getOrderNo()),
                        idempotencyKey
                )
        );
        PaymentPrepareInternalResponse data = prepareResponse == null ? null : prepareResponse.data();
        if (data == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 준비 응답이 비어 있습니다.");
        }
        return data;
    }

    private String buildPaymentPrepareSuccessUrl(String orderNo) {
        return replaceOrderNoTemplate(orderProperties.paymentPrepareSuccessUrlTemplate(), orderNo);
    }

    private String buildPaymentPrepareFailUrl(String orderNo) {
        return replaceOrderNoTemplate(orderProperties.paymentPrepareFailUrlTemplate(), orderNo);
    }

    private String replaceOrderNoTemplate(String template, String orderNo) {
        if (template == null) {
            return "";
        }
        return template.replace("{orderNo}", orderNo);
    }
}
