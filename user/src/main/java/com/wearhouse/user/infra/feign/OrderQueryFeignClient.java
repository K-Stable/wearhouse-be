package com.wearhouse.user.infra.feign;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.user.infra.feign.dto.OrderSummaryItem;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service", path = "/api/v1/orders")
public interface OrderQueryFeignClient {

    @GetMapping
    ApiResponse<List<OrderSummaryItem>> getBuyerOrders(
            @RequestParam("buyerId") Long buyerId,
            @RequestParam("limit") int limit
    );
}
