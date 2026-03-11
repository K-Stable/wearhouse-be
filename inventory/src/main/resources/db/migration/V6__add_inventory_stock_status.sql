ALTER TABLE inventory_stock
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 AFTER available_qty;

UPDATE inventory_stock
SET status = CASE WHEN available_qty <= 0 THEN 0 ELSE 1 END;

CREATE INDEX idx_inventory_stock_seller_status_updated_at
    ON inventory_stock (seller_id, status, updated_at);
