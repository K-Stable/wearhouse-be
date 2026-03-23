ALTER TABLE inventory_outbox_event
    DROP COLUMN aggregate_type,
    DROP COLUMN aggregate_id,
    DROP COLUMN fail_code;
