package com.roambupt.service.user.converter;

import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.user.repository.entity.UserDO;

/**
 * UserDO 与对外模型之间的转换（手写，不引 MapStruct）。
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserAccount toAccount(UserDO user) {
        return new UserAccount(user.getId(), user.getUsername(), user.getPassword(),
                user.getRole(), user.getStatus());
    }

    public static UserVO toVO(UserDO user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getBio(), user.getRole(), 0L, 0L, 0L);
    }
}
