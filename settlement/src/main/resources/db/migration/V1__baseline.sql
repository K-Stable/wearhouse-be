CREATE TABLE IF NOT EXISTS schema_baseline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_baseline (service_name)
SELECT 'settlement-service'
WHERE NOT EXISTS (
    SELECT 1 FROM schema_baseline WHERE service_name = 'settlement-service'
);
