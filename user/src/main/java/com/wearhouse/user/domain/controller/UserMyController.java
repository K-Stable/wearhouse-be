package com.wearhouse.user.domain.controller;

import com.wearhouse.common.security.current.CurrentUser;
import com.wearhouse.common.security.current.LoginBuyer;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.user.domain.dto.request.AddressCreateRequest;
import com.wearhouse.user.domain.dto.request.AddressUpdateRequest;
import com.wearhouse.user.domain.dto.request.PasswordChangeRequest;
import com.wearhouse.user.domain.dto.response.UserAddressResponse;
import com.wearhouse.user.domain.dto.response.UserOrderSummaryResponse;
import com.wearhouse.user.domain.dto.response.UserProfileResponse;
import com.wearhouse.user.domain.service.UserOrderService;
import com.wearhouse.user.domain.service.UserProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserMyController {

    private final UserProfileService userProfileService;
    private final UserOrderService userOrderService;

    public UserMyController(UserProfileService userProfileService, UserOrderService userOrderService) {
        this.userProfileService = userProfileService;
        this.userOrderService = userOrderService;
    }

    @GetMapping("/profile")
    public UserProfileResponse getMyProfile(@CurrentUser LoginUser currentUser) {
        return userProfileService.getMyProfile(currentUser);
    }

    @PatchMapping("/password")
    public void changeMyPassword(
            @CurrentUser LoginUser currentUser,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        userProfileService.changePassword(currentUser, request);
    }

    @GetMapping("/addresses")
    public List<UserAddressResponse> getMyAddresses(@CurrentUser LoginUser currentUser) {
        return userProfileService.getMyAddresses(currentUser);
    }

    @GetMapping("/addresses/default")
    public UserAddressResponse getMyDefaultAddress(@CurrentUser LoginUser currentUser) {
        return userProfileService.getMyDefaultAddress(currentUser);
    }

    @PostMapping("/addresses")
    public UserAddressResponse createAddress(
            @CurrentUser LoginUser currentUser,
            @Valid @RequestBody AddressCreateRequest request
    ) {
        return userProfileService.createAddress(currentUser, request);
    }

    @PatchMapping("/addresses/{addressId}")
    public UserAddressResponse updateAddress(
            @CurrentUser LoginUser currentUser,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressUpdateRequest request
    ) {
        return userProfileService.updateAddress(currentUser, addressId, request);
    }

    @PatchMapping("/addresses/{addressId}/default")
    public UserAddressResponse setDefaultAddress(
            @CurrentUser LoginUser currentUser,
            @PathVariable Long addressId
    ) {
        return userProfileService.setDefaultAddress(currentUser, addressId);
    }

    @DeleteMapping("/addresses/{addressId}")
    public void deleteAddress(
            @CurrentUser LoginUser currentUser,
            @PathVariable Long addressId
    ) {
        userProfileService.deleteAddress(currentUser, addressId);
    }

    @GetMapping("/orders")
    public List<UserOrderSummaryResponse> getMyOrders(
            @LoginBuyer LoginUser currentUser,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return userOrderService.getMyOrders(currentUser, limit);
    }
}
