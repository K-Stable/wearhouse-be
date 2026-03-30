package com.wearhouse.order.delivery.mapper;

import com.wearhouse.order.delivery.dto.response.DeliveryBatchUpdateResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeliveryResponseMapper {

    public DeliveryBatchUpdateResponse toBatchUpdateResponse(List<Long> processedOrderIds) {
        return new DeliveryBatchUpdateResponse(processedOrderIds.size(), processedOrderIds);
    }
}
