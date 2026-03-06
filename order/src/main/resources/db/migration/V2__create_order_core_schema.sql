CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    buyer_id BIGINT NOT NULL,
    status ENUM(
        'PENDING_RESERVE',
        'RESERVE_FAILED',
        'RESERVED',
        'PAYMENT_PENDING',
        'PAYMENT_FAILED',
        'PAID',
        'CONFIRMED',
        'DELIVERED',
        'PURCHASE_CONFIRMED',
        'CANCELLED',
        'REFUND_PENDING'
    ) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'KRW',
    fail_reason_code VARCHAR(50) NULL,
    ordered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_buyer_id (buyer_id),
    KEY idx_orders_status (status)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    option_id BIGINT NULL,
    seller_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    option_name_snapshot VARCHAR(200) NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(15,2) NOT NULL,
    status ENUM(
        'PENDING_RESERVE',
        'RESERVED',
        'CONFIRMED',
        'CANCELLED',
        'REFUNDED'
    ) NOT NULL DEFAULT 'PENDING_RESERVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order_item_order_id (order_id),
    KEY idx_order_item_product_id (product_id),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(50) NULL,
    to_status VARCHAR(50) NOT NULL,
    event_id CHAR(26) NULL,
    changed_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    reason_code VARCHAR(50) NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_status_history_order_id (order_id),
    KEY idx_order_status_history_event_id (event_id),
    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS order_saga (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    saga_id CHAR(26) NOT NULL,
    state ENUM(
        'STARTED',
        'WAITING_INVENTORY',
        'RESERVE_FAILED',
        'WAITING_PAYMENT_PREPARE',
        'WAITING_PAYMENT_RESULT',
        'PAID',
        'CONFIRMED',
        'COMPENSATING',
        'CANCELLED',
        'FAILED'
    ) NOT NULL,
    last_event_id CHAR(26) NULL,
    last_event_type VARCHAR(100) NULL,
    timeout_at DATETIME NULL,
    retry_count INT NOT NULL DEFAULT 0,
    fail_reason_code VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_saga_order_id (order_id),
    UNIQUE KEY uk_order_saga_saga_id (saga_id),
    KEY idx_order_saga_state_timeout (state, timeout_at),
    CONSTRAINT fk_order_saga_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS order_outbox_event (
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
    UNIQUE KEY uk_order_outbox_event_id (event_id),
    KEY idx_order_outbox_status_created_at (status, created_at),
    KEY idx_order_outbox_retry_scan (status, created_at, next_retry_at)
);

CREATE TABLE IF NOT EXISTS order_inbox_event (
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
    UNIQUE KEY uk_order_inbox_event_consumer (event_id, consumer_name),
    KEY idx_order_inbox_status_created_at (status, created_at)
);
