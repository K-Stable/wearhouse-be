package com.wearhouse.order.domain.controller;

import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.domain.dto.request.OrderPreviewRequest;
import com.wearhouse.order.domain.dto.response.OrderPreviewResponse;
import com.wearhouse.order.domain.service.query.OrderPreviewQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class OrderPreviewController {

    private final OrderPreviewQueryService orderPreviewQueryService;

    @PostMapping("/buyer/orders/preview")
    public OrderPreviewResponse previewForBuyer(
            @LoginBuyer LoginUser currentUser,
            @Valid @RequestBody OrderPreviewRequest request
    ) {
        return orderPreviewQueryService.previewForBuyer(currentUser.userId(), request);
    }

    @PostMapping("/buyer/guest/orders/preview")
    public OrderPreviewResponse previewForGuest(@Valid @RequestBody OrderPreviewRequest request) {
        return orderPreviewQueryService.previewForGuest(request);
    }
}
