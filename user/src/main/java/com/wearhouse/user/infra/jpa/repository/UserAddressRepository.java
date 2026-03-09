package com.wearhouse.user.infra.jpa.repository;

import com.wearhouse.user.domain.entity.BuyerAddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAddressRepository extends JpaRepository<BuyerAddressEntity, Long> {

    List<BuyerAddressEntity> findByUserTypeAndUserIdOrderByIsDefaultDescIdDesc(String userType, Long userId);

    Optional<BuyerAddressEntity> findByIdAndUserTypeAndUserId(Long id, String userType, Long userId);

    Optional<BuyerAddressEntity> findFirstByUserTypeAndUserIdAndIsDefaultTrue(String userType, Long userId);

    boolean existsByUserTypeAndUserIdAndIsDefaultTrue(String userType, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BuyerAddressEntity a set a.isDefault = false where a.userType = :userType and a.userId = :userId and a.isDefault = true")
    void clearDefault(@Param("userType") String userType, @Param("userId") Long userId);
}
