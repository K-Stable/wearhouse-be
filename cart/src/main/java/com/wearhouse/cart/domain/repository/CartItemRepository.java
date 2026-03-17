package com.wearhouse.cart.domain.repository;

import com.wearhouse.cart.domain.entity.CartItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    List<CartItemEntity> findAllByBuyerIdOrderByUpdatedAtDescIdDesc(Long buyerId);

    Optional<CartItemEntity> findByBuyerIdAndProductIdAndOptionId(Long buyerId, Long productId, Long optionId);

    Optional<CartItemEntity> findByIdAndBuyerId(Long id, Long buyerId);

    void deleteAllByBuyerId(Long buyerId);
}
