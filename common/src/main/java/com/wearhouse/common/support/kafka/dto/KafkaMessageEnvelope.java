package com.wearhouse.common.support.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KafkaMessageEnvelope(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        LocalDateTime occurredAt,
        Integer version,
        String producer,
        Map<String, Object> payload
) {
}
