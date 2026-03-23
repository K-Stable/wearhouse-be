ALTER TABLE order_inbox_event
    DROP COLUMN event_type,
    DROP COLUMN topic,
    DROP COLUMN partition_key,
    DROP COLUMN payload,
    DROP COLUMN fail_count,
    DROP COLUMN fail_reason_code,
    DROP COLUMN fail_reason_message;
