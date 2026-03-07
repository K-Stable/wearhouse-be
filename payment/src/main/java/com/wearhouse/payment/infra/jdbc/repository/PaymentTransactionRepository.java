package com.wearhouse.payment.infra.jdbc.repository;

import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentTransactionRepository {

    private static final RowMapper<PaymentTransactionRecord> ROW_MAPPER = new PaymentTransactionRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public PaymentTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PaymentTransactionRecord> findByOrderId(Long orderId) {
        List<PaymentTransactionRecord> rows = jdbcTemplate.query(
                """
                        SELECT id, payment_id, order_id, order_no, amount, payment_method, status,
                               reason_code, expires_at, authorized_at, failed_at
                        FROM payment_transaction
                        WHERE order_id = ?
                        """,
                ROW_MAPPER,
                orderId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public void insertPending(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime expiresAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO payment_transaction (
                            payment_id, order_id, order_no, amount, payment_method, status, expires_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                PaymentStatus.PENDING.name(),
                expiresAt
        );
    }

    public void insertAuthorized(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO payment_transaction (
                            payment_id, order_id, order_no, amount, payment_method, status, authorized_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                PaymentStatus.AUTHORIZED.name(),
                authorizedAt
        );
    }

    public void insertFailed(
            String paymentId,
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod,
            String reasonCode,
            LocalDateTime failedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO payment_transaction (
                            payment_id, order_id, order_no, amount, payment_method, status, reason_code, failed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                paymentId,
                orderId,
                orderNo,
                amount,
                paymentMethod,
                PaymentStatus.FAILED.name(),
                reasonCode,
                failedAt
        );
    }

    public int markFailedIfPending(Long orderId, String reasonCode, LocalDateTime failedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE payment_transaction
                        SET status = ?, reason_code = ?, failed_at = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE order_id = ? AND status = ?
                        """,
                PaymentStatus.FAILED.name(),
                reasonCode,
                failedAt,
                orderId,
                PaymentStatus.PENDING.name()
        );
    }

    public List<PaymentTransactionRecord> findTimeoutCandidates(LocalDateTime now, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, payment_id, order_id, order_no, amount, payment_method, status,
                               reason_code, expires_at, authorized_at, failed_at
                        FROM payment_transaction
                        WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?
                        ORDER BY id
                        LIMIT ?
                        """,
                ROW_MAPPER,
                PaymentStatus.PENDING.name(),
                now,
                limit
        );
    }

    private static final class PaymentTransactionRowMapper implements RowMapper<PaymentTransactionRecord> {
        @Override
        public PaymentTransactionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PaymentTransactionRecord(
                    rs.getLong("id"),
                    rs.getString("payment_id"),
                    rs.getLong("order_id"),
                    rs.getString("order_no"),
                    rs.getBigDecimal("amount"),
                    rs.getString("payment_method"),
                    PaymentStatus.valueOf(rs.getString("status")),
                    rs.getString("reason_code"),
                    toLocalDateTime(rs.getTimestamp("expires_at")),
                    toLocalDateTime(rs.getTimestamp("authorized_at")),
                    toLocalDateTime(rs.getTimestamp("failed_at"))
            );
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
