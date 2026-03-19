ALTER TABLE payment_transaction
    ADD COLUMN payment_key VARCHAR(80) NULL AFTER payment_method,
    ADD COLUMN payment_session_id VARCHAR(80) NULL AFTER payment_key,
    ADD COLUMN merchant_key VARCHAR(120) NULL AFTER payment_session_id,
    ADD COLUMN payer_address VARCHAR(100) NULL AFTER merchant_key,
    ADD COLUMN token_address VARCHAR(100) NULL AFTER payer_address,
    ADD COLUMN command_id VARCHAR(80) NULL AFTER token_address,
    ADD COLUMN command_status VARCHAR(40) NULL AFTER command_id,
    ADD COLUMN tx_hash VARCHAR(120) NULL AFTER command_status;

ALTER TABLE payment_transaction
    ADD UNIQUE KEY uk_payment_transaction_payment_key (payment_key);

CREATE TABLE IF NOT EXISTS payment_webhook_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    occurred_at DATETIME NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_webhook_event_event_id (event_id),
    KEY idx_payment_webhook_event_event_type_received_at (event_type, received_at)
);
