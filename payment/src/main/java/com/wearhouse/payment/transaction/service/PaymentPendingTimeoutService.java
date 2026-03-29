package com.wearhouse.payment.transaction.service;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentPendingTimeoutService {

    private static final String TIMEOUT_REASON_CODE = "PAYMENT_TIMEOUT";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionUpdateService paymentTransactionUpdateService;
    private final PaymentEventPublishService paymentEventPublishService;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final PaymentMockProperties paymentMockProperties;

    @WriteTx
    public int failExpiredPendingPayments() {
        int failedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransactionEntity candidate : paymentTransactionRepository.findTimeoutCandidates(now, paymentMockProperties.timeoutBatchSize())) {
            int updated = paymentTransactionUpdateService.markFailedIfPending(
                    candidate.getOrderId(),
                    TIMEOUT_REASON_CODE,
                    now
            );
            if (updated > 0) {
                failedCount++;
                paymentEventPublishService.publishFailed(
                        candidate.getOrderId(),
                        candidate.getOrderNo(),
                        candidate.getPaymentId(),
                        TIMEOUT_REASON_CODE,
                        "결제 대기 시간이 만료되었습니다.",
                        now
                );
            }
        }
        paymentKafkaFlowMetrics.incrementTimeoutFailed(failedCount);
        return failedCount;
    }
}
