package com.wearhouse.payment.webhook.controller;

import com.wearhouse.payment.webhook.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/pay")
    public ResponseEntity<Void> receivePayWebhook(
            @RequestHeader("X-Webhook-Timestamp") String timestamp,
            @RequestHeader("X-Webhook-Signature") String signature,
            @RequestBody String rawBody
    ) {
        paymentWebhookService.handle(timestamp, signature, rawBody);
        return ResponseEntity.ok().build();
    }
}
