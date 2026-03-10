ALTER TABLE product_image
    MODIFY COLUMN image_type ENUM('MAIN', 'PREVIEW', 'DETAIL') NOT NULL;

INSERT INTO product_image (product_id, image_type, image_url, sort_order, created_at, updated_at)
SELECT p.id, 'MAIN', p.main_image_url, 0, p.created_at, p.updated_at
FROM product p
WHERE p.main_image_url IS NOT NULL
  AND p.main_image_url <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM product_image pi
    WHERE pi.product_id = p.id
      AND pi.image_type = 'MAIN'
);
