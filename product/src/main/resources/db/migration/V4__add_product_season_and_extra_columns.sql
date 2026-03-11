ALTER TABLE product
    ADD COLUMN details VARCHAR(500) NULL;

ALTER TABLE product
    ADD COLUMN size_guide VARCHAR(500) NULL;

ALTER TABLE product
    ADD COLUMN shipping VARCHAR(500) NULL;

ALTER TABLE product
    ADD COLUMN season_id BIGINT NULL;

UPDATE product
SET details = description
WHERE details IS NULL
  AND description IS NOT NULL;

CREATE TABLE IF NOT EXISTS product_season (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_product_season_seller_id (seller_id),
    KEY idx_product_season_created_at (created_at)
);

ALTER TABLE product
    ADD CONSTRAINT fk_product_season
    FOREIGN KEY (season_id) REFERENCES product_season(id)
    ON DELETE SET NULL ON UPDATE RESTRICT;
