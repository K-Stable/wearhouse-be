package com.wearhouse.payment.infra.jdbc.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryReceive(
            String eventId,
            String consumerName,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO payment_inbox_event (
                                event_id, consumer_name, event_type, topic, partition_key, payload, status
                            ) VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED')
                            """,
                    eventId,
                    consumerName,
                    eventType,
                    topic,
                    partitionKey,
                    payload
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public void markProcessed(String eventId, String consumerName) {
        jdbcTemplate.update(
                """
                        UPDATE payment_inbox_event
                        SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ? AND consumer_name = ?
                        """,
                eventId,
                consumerName
        );
    }

    public void markFailed(String eventId, String consumerName, String reasonCode, String reasonMessage) {
        jdbcTemplate.update(
                """
                        UPDATE payment_inbox_event
                        SET status = 'FAILED',
                            fail_count = fail_count + 1,
                            fail_reason_code = ?,
                            fail_reason_message = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE event_id = ? AND consumer_name = ?
                        """,
                reasonCode,
                reasonMessage,
                eventId,
                consumerName
        );
    }
}
