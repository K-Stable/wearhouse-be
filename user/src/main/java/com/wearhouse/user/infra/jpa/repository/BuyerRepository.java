package com.wearhouse.user.infra.jpa.repository;

import com.wearhouse.user.domain.entity.BuyerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyerRepository extends JpaRepository<BuyerEntity, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByLoginIdOrEmail(String loginId, String email);

    Optional<BuyerEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
