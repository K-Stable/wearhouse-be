CREATE TABLE IF NOT EXISTS payment_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(40) NULL,
    amount DECIMAL(15,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status ENUM('PENDING', 'AUTHORIZED', 'FAILED') NOT NULL,
    reason_code VARCHAR(50) NULL,
    expires_at DATETIME NULL,
    authorized_at DATETIME NULL,
    failed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_transaction_payment_id (payment_id),
    UNIQUE KEY uk_payment_transaction_order_id (order_id),
    KEY idx_payment_transaction_status_expires_at (status, expires_at)
);

CREATE TABLE IF NOT EXISTS payment_inbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(26) NOT NULL,
    consumer_name VARCHAR(80) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    partition_key VARCHAR(100) NULL,
    payload JSON NOT NULL,
    status ENUM('RECEIVED', 'PROCESSED', 'FAILED') NOT NULL DEFAULT 'RECEIVED',
    fail_count INT NOT NULL DEFAULT 0,
    fail_reason_code VARCHAR(50) NULL,
    fail_reason_message VARCHAR(255) NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_inbox_event_consumer (event_id, consumer_name),
    KEY idx_payment_inbox_event_status_created_at (status, created_at)
);
