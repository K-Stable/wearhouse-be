package com.wearhouse.user.buyer.service;

import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.user.infra.jpa.repository.BuyerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerUserQueryService {

    private final BuyerRepository buyerRepository;

    @ReadTx
    public boolean isLoginIdAvailable(String loginId) {
        return !buyerRepository.existsByLoginId(loginId);
    }
}
