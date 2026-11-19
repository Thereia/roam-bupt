package com.roambupt.service.user.service;

import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.vo.UserVO;

import java.util.List;
import java.util.Optional;

/**
 * 用户读服务。跨域只能调它，不能直接碰 mapper。
 */
public interface UserQueryService {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserVO> getById(long uid);

    Optional<UserAccount> findAccountById(long uid);

    /** 我关注了谁，供关注流使用。 */
    List<Long> findFollowingIds(long uid);
}
