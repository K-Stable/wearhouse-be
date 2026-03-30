package com.wearhouse.payment.internal.mapper;

import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.internal.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.internal.dto.response.WalletPrepareResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentInternalResponseMapper {

    public WalletPrepareResponse toPrepareResponse(
            String checkoutSessionId,
            String checkoutUrl,
            String appLaunchUrl,
            String checkoutExpiresAt
    ) {
        return new WalletPrepareResponse(
                checkoutSessionId,
                checkoutUrl,
                appLaunchUrl,
                checkoutExpiresAt
        );
    }

    public PaymentConfirmResponse toConfirmResponse(
            PaymentTransactionEntity transaction,
            PaymentStatus paymentStatus,
            String commandStatus,
            String reasonCode
    ) {
        return new PaymentConfirmResponse(
                transaction.getOrderId(),
                transaction.getOrderNo(),
                transaction.getPaymentId(),
                paymentStatus.name(),
                commandStatus,
                reasonCode
        );
    }
}
