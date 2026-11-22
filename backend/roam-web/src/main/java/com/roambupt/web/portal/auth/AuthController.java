package com.roambupt.web.portal.auth;

import com.roambupt.api.model.dto.LoginCommand;
import com.roambupt.api.model.dto.RegisterCommand;
import com.roambupt.api.model.vo.AuthResult;
import com.roambupt.api.model.vo.ReqInfo;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.roambupt.web.global.ClientIpResolver;

/**
 * 认证接口：注册、登录、登出、当前用户。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResVo<AuthResult> register(@Valid @RequestBody RegisterCommand command,
                                      HttpServletRequest request) {
        return ResVo.ok(authService.register(command, clientIpResolver.resolve(request)));
    }

    @PostMapping("/login")
    public ResVo<AuthResult> login(@Valid @RequestBody LoginCommand command,
                                   HttpServletRequest request) {
        return ResVo.ok(authService.login(command, clientIpResolver.resolve(request)));
    }

    @PostMapping("/logout")
    public ResVo<Void> logout() {
        ReqInfo info = CurrentUserContext.get();
        authService.logout(info == null ? null : info.jti());
        return ResVo.ok();
    }

    @GetMapping("/me")
    public ResVo<UserVO> me() {
        Long uid = CurrentUserContext.uid();
        return ResVo.ok(authService.currentUser(uid == null ? 0L : uid));
    }

}
