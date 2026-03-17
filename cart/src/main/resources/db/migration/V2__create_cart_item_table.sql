CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    main_image_url VARCHAR(500) NOT NULL,
    size_label VARCHAR(60) NOT NULL,
    color_label VARCHAR(60) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_cart_item_buyer_product_option (buyer_id, product_id, option_id),
    KEY idx_cart_item_buyer_id (buyer_id),
    KEY idx_cart_item_buyer_updated_at (buyer_id, updated_at, id)
);
