package com.roambupt.service.auth.service.impl;

import com.roambupt.api.model.dto.LoginCommand;
import com.roambupt.api.model.dto.RegisterCommand;
import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.event.LoginEvent;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.AuthResult;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.core.jwt.IssuedToken;
import com.roambupt.core.jwt.JwtProperties;
import com.roambupt.core.jwt.JwtSupport;
import com.roambupt.core.limit.LoginRateLimiter;
import com.roambupt.core.session.SessionStore;
import com.roambupt.core.session.StoreUnavailableException;
import com.roambupt.service.auth.service.AuthService;
import com.roambupt.service.user.service.UserCommandService;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * 认证实现。
 *
 * <p>本类方法不是事务方法。建号走 {@link UserCommandService} 自己的事务，
 * 会话写 Redis 发生在事务提交之后，避免"号已建、事务回滚"或"Redis 失败把号回滚"。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 用户不存在时也跑一次 BCrypt，抹平时序差异。 */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final PasswordEncoder passwordEncoder;
    private final JwtSupport jwtSupport;
    private final JwtProperties jwtProperties;
    private final SessionStore sessionStore;
    private final LoginRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    public AuthServiceImpl(UserQueryService userQueryService,
                           UserCommandService userCommandService,
                           PasswordEncoder passwordEncoder,
                           JwtSupport jwtSupport,
                           JwtProperties jwtProperties,
                           SessionStore sessionStore,
                           LoginRateLimiter rateLimiter,
                           ApplicationEventPublisher eventPublisher) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
        this.passwordEncoder = passwordEncoder;
        this.jwtSupport = jwtSupport;
        this.jwtProperties = jwtProperties;
        this.sessionStore = sessionStore;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AuthResult register(RegisterCommand command, String clientIp) {
        String username = normalize(command.username());
        if (!rateLimiter.tryAcquire(clientIp, username)) {
            throw new BusinessException(StatusEnum.RATE_LIMITED, "尝试太频繁，每分钟最多 5 次，请稍后再试");
        }
        String encoded = passwordEncoder.encode(command.password());
        long uid;
        try {
            uid = userCommandService.createAccount(username, encoded, command.nickname());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(StatusEnum.CONFLICT, "用户名已存在");
        }
        return issue(uid, username, "账号已创建，请直接登录");
    }

    @Override
    public AuthResult login(LoginCommand command, String clientIp) {
        String username = normalize(command.username());
        if (!rateLimiter.tryAcquire(clientIp, username)) {
            publish(null, username, false, "RATE_LIMITED", clientIp);
            throw new BusinessException(StatusEnum.RATE_LIMITED, "尝试太频繁，每分钟最多 5 次，请稍后再试");
        }

        Optional<UserAccount> found = userQueryService.findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(command.password(), DUMMY_HASH);
            publish(null, username, false, "NO_USER", clientIp);
            throw new BusinessException(StatusEnum.UNAUTHORIZED, "用户名或密码错误");
        }

        UserAccount account = found.get();
        if (account.status() == null || account.status() != 1) {
            passwordEncoder.matches(command.password(), DUMMY_HASH);
            publish(account.id(), username, false, "DISABLED", clientIp);
            throw new BusinessException(StatusEnum.UNAUTHORIZED, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(command.password(), account.password())) {
            publish(account.id(), username, false, "BAD_PASSWORD", clientIp);
            throw new BusinessException(StatusEnum.UNAUTHORIZED, "用户名或密码错误");
        }

        userCommandService.updateLastLoginTime(account.id());
        publish(account.id(), username, true, null, clientIp);
        return issue(account.id(), username, "登录成功但会话创建失败，请重试");
    }

    @Override
    public void logout(String jti) {
        sessionStore.remove(jti);
    }

    @Override
    public UserVO currentUser(long uid) {
        return userQueryService.getById(uid)
                .orElseThrow(() -> new BusinessException(StatusEnum.NOT_FOUND));
    }

    private AuthResult issue(long uid, String username, String unavailableMessage) {
        IssuedToken issued = jwtSupport.issue(uid, username);
        try {
            sessionStore.save(issued.jti(), uid, jwtProperties.ttl());
        } catch (StoreUnavailableException e) {
            throw new BusinessException(StatusEnum.SERVICE_UNAVAILABLE, unavailableMessage);
        }
        UserVO user = userQueryService.getById(uid)
                .orElseThrow(() -> new BusinessException(StatusEnum.NOT_FOUND));
        return new AuthResult(issued.token(), user);
    }

    private void publish(Long uid, String username, boolean success, String reason, String ip) {
        eventPublisher.publishEvent(new LoginEvent(uid, username, success, reason, ip));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
