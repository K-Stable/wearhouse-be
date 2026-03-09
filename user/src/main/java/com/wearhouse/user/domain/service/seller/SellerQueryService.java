package com.wearhouse.user.domain.service.seller;

import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.user.infra.jpa.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerQueryService {

    private final SellerRepository sellerRepository;

    @ReadTx
    public boolean isLoginIdAvailable(String loginId) {
        return !sellerRepository.existsByLoginId(loginId);
    }
}
