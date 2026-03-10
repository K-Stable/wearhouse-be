ALTER TABLE inventory_stock
    ADD COLUMN seller_id BIGINT NULL AFTER sku_id,
    ADD COLUMN product_id BIGINT NULL AFTER seller_id,
    ADD COLUMN product_name VARCHAR(150) NULL AFTER product_id,
    ADD COLUMN product_price DECIMAL(15,2) NULL AFTER product_name,
    ADD COLUMN product_category VARCHAR(60) NULL AFTER product_price,
    ADD COLUMN option_size VARCHAR(60) NULL AFTER product_category,
    ADD COLUMN option_color VARCHAR(60) NULL AFTER option_size,
    ADD COLUMN main_image_url VARCHAR(500) NULL AFTER option_color;

CREATE INDEX idx_inventory_stock_seller_updated_at
    ON inventory_stock (seller_id, updated_at);
