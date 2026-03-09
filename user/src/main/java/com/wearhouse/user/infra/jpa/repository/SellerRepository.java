package com.wearhouse.user.infra.jpa.repository;

import com.wearhouse.user.domain.entity.SellerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<SellerEntity, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByLoginIdOrEmail(String loginId, String email);

    Optional<SellerEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
