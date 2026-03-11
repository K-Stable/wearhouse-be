package com.wearhouse.inventory.domain.repository;

import com.wearhouse.inventory.domain.entity.InventoryReservationEntity;
import com.wearhouse.inventory.domain.model.InventoryReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, Long> {

    List<InventoryReservationEntity> findByOrderIdAndStatus(Long orderId, InventoryReservationStatus status);

    List<InventoryReservationEntity> findByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            InventoryReservationStatus status,
            LocalDateTime expiresAt,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE InventoryReservationEntity reservation
               SET reservation.status = com.wearhouse.inventory.domain.model.InventoryReservationStatus.RELEASED,
                   reservation.releasedAt = :releasedAt
             WHERE reservation.id = :reservationId
               AND reservation.status = com.wearhouse.inventory.domain.model.InventoryReservationStatus.RESERVED
            """)
    int markReleasedIfReserved(
            @Param("reservationId") Long reservationId,
            @Param("releasedAt") LocalDateTime releasedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE InventoryReservationEntity reservation
               SET reservation.status = com.wearhouse.inventory.domain.model.InventoryReservationStatus.CONFIRMED,
                   reservation.confirmedAt = :confirmedAt
             WHERE reservation.id = :reservationId
               AND reservation.status = com.wearhouse.inventory.domain.model.InventoryReservationStatus.RESERVED
            """)
    int markConfirmedIfReserved(
            @Param("reservationId") Long reservationId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );
}
