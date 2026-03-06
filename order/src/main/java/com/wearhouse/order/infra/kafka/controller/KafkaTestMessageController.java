package com.wearhouse.order.infra.kafka.controller;

import com.wearhouse.order.infra.kafka.dto.KafkaTestPublishResponse;
import com.wearhouse.order.infra.kafka.service.KafkaTestMessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/kafka")
public class KafkaTestMessageController {

    private final KafkaTestMessageService kafkaTestMessageService;

    public KafkaTestMessageController(KafkaTestMessageService kafkaTestMessageService) {
        this.kafkaTestMessageService = kafkaTestMessageService;
    }

    @PostMapping("/test-publish")
    public KafkaTestPublishResponse publishTestMessage(@RequestParam(required = false) String message) {
        return kafkaTestMessageService.publishTestMessage(message);
    }
}
