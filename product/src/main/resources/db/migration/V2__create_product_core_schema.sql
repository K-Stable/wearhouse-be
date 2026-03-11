CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    category VARCHAR(60) NOT NULL,
    description TEXT NULL,
    status ENUM('PENDING', 'RELEASED', 'SOLD_OUT', 'HIDDEN') NOT NULL DEFAULT 'PENDING',
    main_image_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_product_seller_id (seller_id),
    KEY idx_product_status_created_at (status, created_at),
    KEY idx_product_category_created_at (category, created_at)
);

CREATE TABLE IF NOT EXISTS product_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    size_label VARCHAR(60) NOT NULL,
    color_label VARCHAR(60) NOT NULL,
    stock_quantity INT NOT NULL,
    additional_price DECIMAL(15,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_product_option_value (product_id, size_label, color_label),
    KEY idx_product_option_product_id (product_id),
    CONSTRAINT fk_product_option_product
        FOREIGN KEY (product_id) REFERENCES product(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_type ENUM('PREVIEW', 'DETAIL') NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_product_image_product_type_sort (product_id, image_type, sort_order),
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES product(id)
        ON DELETE CASCADE ON UPDATE RESTRICT
);
