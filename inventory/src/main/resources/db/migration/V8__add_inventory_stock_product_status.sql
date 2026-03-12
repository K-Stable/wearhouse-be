ALTER TABLE inventory_stock
    ADD COLUMN product_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER product_category;

CREATE INDEX idx_inventory_stock_seller_product_status_updated_at
    ON inventory_stock (seller_id, product_status, updated_at);
