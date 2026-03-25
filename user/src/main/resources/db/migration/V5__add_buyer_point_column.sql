SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buyer'
      AND COLUMN_NAME = 'point'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE buyer ADD COLUMN point VARCHAR(30) NOT NULL DEFAULT ''0'' AFTER phone',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
