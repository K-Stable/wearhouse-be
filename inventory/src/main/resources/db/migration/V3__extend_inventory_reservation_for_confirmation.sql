ALTER TABLE inventory_reservation
    MODIFY COLUMN status ENUM('RESERVED', 'RELEASED', 'CONFIRMED') NOT NULL;

ALTER TABLE inventory_reservation
    ADD COLUMN confirmed_at DATETIME NULL AFTER released_at;

CREATE INDEX idx_inventory_reservation_status_expires_at
    ON inventory_reservation (status, expires_at);
