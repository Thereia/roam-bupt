package com.roambupt.service.auth.service;

import com.roambupt.api.model.dto.LoginCommand;
import com.roambupt.api.model.dto.RegisterCommand;
import com.roambupt.api.model.vo.AuthResult;
import com.roambupt.api.model.vo.UserVO;

/**
 * 认证：注册、登录、登出、当前用户。
 */
public interface AuthService {

    AuthResult register(RegisterCommand command, String clientIp);

    AuthResult login(LoginCommand command, String clientIp);

    void logout(String jti);

    UserVO currentUser(long uid);
}
