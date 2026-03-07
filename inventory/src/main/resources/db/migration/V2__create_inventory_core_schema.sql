CREATE TABLE IF NOT EXISTS inventory_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    available_qty INT NOT NULL,
    reserved_qty INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inventory_stock_sku_id (sku_id)
);

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reservation_id VARCHAR(40) NOT NULL,
    source_event_id CHAR(26) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(40) NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status ENUM('RESERVED', 'RELEASED') NOT NULL,
    expires_at DATETIME NOT NULL,
    released_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inventory_reservation_id (reservation_id),
    KEY idx_inventory_reservation_order_status (order_id, status),
    KEY idx_inventory_reservation_source_event_id (source_event_id)
);

CREATE TABLE IF NOT EXISTS inventory_inbox_event (
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
    UNIQUE KEY uk_inventory_inbox_event_consumer (event_id, consumer_name),
    KEY idx_inventory_inbox_event_status_created_at (status, created_at)
);
