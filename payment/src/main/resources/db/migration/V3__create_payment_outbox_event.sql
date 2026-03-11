CREATE TABLE IF NOT EXISTS payment_outbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(26) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status ENUM('READY', 'SEND_SUCCESS', 'SEND_FAIL', 'DEAD') NOT NULL DEFAULT 'READY',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    published_at DATETIME NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_outbox_event_id (event_id),
    KEY idx_payment_outbox_status_created_at (status, created_at),
    KEY idx_payment_outbox_retry_scan (status, created_at, next_retry_at)
);

