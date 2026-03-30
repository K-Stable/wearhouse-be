package com.wearhouse.user.internal.mapper;

import com.wearhouse.user.domain.dto.response.InternalUserAuthAccountResponse;
import com.wearhouse.user.domain.model.UserAuthAccount;
import org.springframework.stereotype.Component;

@Component
public class UserInternalResponseMapper {

    public InternalUserAuthAccountResponse toInternalUserAuthAccountResponse(UserAuthAccount account) {
        return new InternalUserAuthAccountResponse(
                account.userId(),
                account.userType().name(),
                account.email(),
                account.passwordHash(),
                account.status(),
                account.userVersion()
        );
    }
}
