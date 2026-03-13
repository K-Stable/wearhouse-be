UPDATE inventory_stock
SET product_status = 'PENDING'
WHERE product_status IS NULL
   OR product_status NOT IN ('PENDING', 'RELEASED', 'SOLD_OUT', 'HIDDEN');

ALTER TABLE inventory_stock
    MODIFY COLUMN product_status ENUM('PENDING', 'RELEASED', 'SOLD_OUT', 'HIDDEN') NOT NULL DEFAULT 'PENDING';

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inventory_stock'
      AND index_name = 'idx_inventory_stock_seller_status_updated_at'
);
SET @drop_idx_sql := IF(
    @idx_exists > 0,
    'DROP INDEX idx_inventory_stock_seller_status_updated_at ON inventory_stock',
    'SELECT 1'
);
PREPARE drop_idx_stmt FROM @drop_idx_sql;
EXECUTE drop_idx_stmt;
DEALLOCATE PREPARE drop_idx_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inventory_stock'
      AND column_name = 'status'
);
SET @drop_col_sql := IF(
    @col_exists > 0,
    'ALTER TABLE inventory_stock DROP COLUMN status',
    'SELECT 1'
);
PREPARE drop_col_stmt FROM @drop_col_sql;
EXECUTE drop_col_stmt;
DEALLOCATE PREPARE drop_col_stmt;
