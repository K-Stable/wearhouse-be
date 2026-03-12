ALTER TABLE order_saga
    DROP INDEX idx_order_saga_state_timeout;

ALTER TABLE order_saga
    DROP COLUMN timeout_at,
    DROP COLUMN retry_count;
