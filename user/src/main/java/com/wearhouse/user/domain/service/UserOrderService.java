package com.wearhouse.user.domain.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.user.domain.dto.response.UserOrderSummaryResponse;
import com.wearhouse.user.domain.exception.UserErrorCode;
import com.wearhouse.user.domain.model.UserType;
import com.wearhouse.user.infra.feign.OrderQueryFeignClient;
import com.wearhouse.user.infra.feign.dto.OrderSummaryItem;
import feign.FeignException;
import java.util.List;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.ReadTx;

@Service
public class UserOrderService {

    private final OrderQueryFeignClient orderQueryFeignClient;

    public UserOrderService(OrderQueryFeignClient orderQueryFeignClient) {
        this.orderQueryFeignClient = orderQueryFeignClient;
    }

    @ReadTx
    public List<UserOrderSummaryResponse> getMyOrders(CurrentUserPrincipal currentUser, int limit) {
        UserType userType = parseUserType(currentUser.userType());
        if (userType != UserType.BUYER) {
            return List.of();
        }
        try {
            ApiResponse<List<OrderSummaryItem>> response = orderQueryFeignClient.getBuyerOrders(currentUser.userId(), limit);
            if (response == null || !response.success() || response.data() == null) {
                throw new ErrorException(UserErrorCode.ORDER_SERVICE_INVALID_RESPONSE);
            }
            return response.data().stream()
                    .map(item -> new UserOrderSummaryResponse(
                            item.orderNo(),
                            item.status(),
                            item.payAmount(),
                            item.orderedAt()
                    ))
                    .toList();
        } catch (FeignException exception) {
            throw new ErrorException(UserErrorCode.ORDER_SERVICE_UNAVAILABLE);
        }
    }

    private UserType parseUserType(String raw) {
        try {
            return UserType.valueOf(raw);
        } catch (Exception exception) {
            throw new ErrorException(UserErrorCode.USER_TYPE_INVALID);
        }
    }
}
