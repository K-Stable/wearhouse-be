UPDATE order_outbox_event
SET status = 'SUCCESS'
WHERE status = 'SEND_SUCCESS';

UPDATE order_outbox_event
SET status = 'FAIL'
WHERE status IN ('SEND_FAIL', 'DEAD');

ALTER TABLE order_outbox_event
    MODIFY COLUMN status ENUM('READY', 'SUCCESS', 'FAIL') NOT NULL DEFAULT 'READY';

ALTER TABLE order_outbox_event
    CHANGE COLUMN published_at sent_at DATETIME NULL,
    CHANGE COLUMN error_code fail_code VARCHAR(50) NULL,
    CHANGE COLUMN error_message fail_message VARCHAR(255) NULL;

ALTER TABLE order_outbox_event
    DROP INDEX idx_order_outbox_retry_scan;

ALTER TABLE order_outbox_event
    DROP COLUMN retry_count,
    DROP COLUMN next_retry_at;
