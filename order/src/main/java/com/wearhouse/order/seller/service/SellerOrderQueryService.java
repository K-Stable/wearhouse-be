package com.wearhouse.order.seller.service;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.seller.dto.response.SellerOrderListPageResponse;
import com.wearhouse.order.seller.mapper.SellerOrderResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerOrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private final OrderRepository orderRepository;
    private final SellerOrderResponseMapper sellerOrderResponseMapper;

    @ReadTx
    public SellerOrderListPageResponse getSellerOrders(
            LoginUser currentUser,
            String keyword,
            OrderStatus status,
            Integer page,
            Integer size
    ) {
        int pageNumber = resolvePage(page);
        int pageSize = resolveSize(size);
        Page<OrderEntity> orders = orderRepository.findOrdersForSellerDashboard(
                normalizeKeyword(keyword),
                status,
                PageRequest.of(pageNumber, pageSize)
        );

        var content = orders.getContent().stream()
                .map(sellerOrderResponseMapper::toListItem)
                .toList();

        return sellerOrderResponseMapper.toPageResponse(orders, content);
    }

    private int resolvePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return size;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
