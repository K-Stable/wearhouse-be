CREATE TABLE IF NOT EXISTS delivery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    courier_code VARCHAR(80) NOT NULL,
    invoice_no VARCHAR(120) NOT NULL,
    order_id BIGINT NOT NULL,
    status ENUM('IN_DELIVERY', 'DELIVERED') NOT NULL DEFAULT 'IN_DELIVERY',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_delivery_order_id (order_id),
    UNIQUE KEY uk_delivery_invoice_no (invoice_no),
    KEY idx_delivery_status_updated_at (status, updated_at),
    CONSTRAINT fk_delivery_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);
