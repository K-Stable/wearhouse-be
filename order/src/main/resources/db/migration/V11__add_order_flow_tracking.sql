ALTER TABLE order_outbox_event
    ADD INDEX idx_order_outbox_partition_key (partition_key);

ALTER TABLE order_inbox_event
    ADD COLUMN event_type VARCHAR(100) NULL AFTER consumer_name,
    ADD COLUMN topic VARCHAR(120) NULL AFTER event_type,
    ADD COLUMN partition_key VARCHAR(100) NULL AFTER topic,
    ADD COLUMN payload JSON NULL AFTER partition_key,
    ADD COLUMN order_id BIGINT NULL AFTER payload,
    ADD COLUMN order_no VARCHAR(40) NULL AFTER order_id,
    ADD COLUMN fail_reason_code VARCHAR(50) NULL AFTER processed_at,
    ADD COLUMN fail_reason_message VARCHAR(255) NULL AFTER fail_reason_code;

ALTER TABLE order_inbox_event
    ADD INDEX idx_order_inbox_order_id (order_id);

CREATE TABLE IF NOT EXISTS order_saga_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_state VARCHAR(40) NULL,
    to_state VARCHAR(40) NOT NULL,
    event_id CHAR(26) NULL,
    reason_code VARCHAR(50) NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order_saga_history_order_id (order_id),
    KEY idx_order_saga_history_event_id (event_id),
    CONSTRAINT fk_order_saga_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE CASCADE ON UPDATE RESTRICT
);
