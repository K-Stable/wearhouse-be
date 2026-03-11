ALTER TABLE buyer
    ADD COLUMN login_id VARCHAR(255) NULL AFTER id,
    ADD COLUMN phone VARCHAR(30) NOT NULL DEFAULT '' AFTER name;

UPDATE buyer
SET login_id = email
WHERE login_id IS NULL OR login_id = '';

ALTER TABLE buyer
    MODIFY COLUMN login_id VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_buyer_login_id UNIQUE (login_id);

ALTER TABLE seller
    ADD COLUMN login_id VARCHAR(255) NULL AFTER id,
    ADD COLUMN phone VARCHAR(30) NOT NULL DEFAULT '' AFTER name,
    ADD COLUMN seller_no VARCHAR(100) NOT NULL DEFAULT '' AFTER phone;

UPDATE seller
SET login_id = email
WHERE login_id IS NULL OR login_id = '';

ALTER TABLE seller
    MODIFY COLUMN login_id VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_seller_login_id UNIQUE (login_id);
