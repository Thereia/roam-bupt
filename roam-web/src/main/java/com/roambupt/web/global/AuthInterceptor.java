package com.roambupt.web.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.vo.ReqInfo;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.core.config.AuthProperties;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.core.jwt.InvalidTokenException;
import com.roambupt.core.jwt.JwtSupport;
import com.roambupt.core.jwt.ParsedToken;
import com.roambupt.core.session.SessionStatus;
import com.roambupt.core.session.SessionStore;
import com.roambupt.service.user.service.UserQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * 鉴权拦截器：解析 token → 校验会话白名单 → 查用户状态 → 写上下文。
 *
 * <p>三种失败严格区分：token 本身无效 401；会话不存在 401；存储不可用 503。
 * 开启 {@code roam.auth.redis-fail-open} 时，存储不可用降级为匿名放行并打 WARN。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtSupport jwtSupport;
    private final SessionStore sessionStore;
    private final UserQueryService userQueryService;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtSupport jwtSupport,
                           SessionStore sessionStore,
                           UserQueryService userQueryService,
                           AuthProperties authProperties,
                           ObjectMapper objectMapper) {
        this.jwtSupport = jwtSupport;
        this.sessionStore = sessionStore;
        this.userQueryService = userQueryService;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        boolean optional = isOptional(handler);
        String token = resolveToken(request);
        if (token == null) {
            return optional || reject(response, 401, "未登录");
        }

        ParsedToken parsed;
        try {
            parsed = jwtSupport.parse(token);
        } catch (InvalidTokenException e) {
            return optional || reject(response, 401, "登录已失效，请重新登录");
        }

        SessionStatus status = sessionStore.check(parsed.jti());
        if (status == SessionStatus.UNAVAILABLE) {
            if (authProperties.redisFailOpen()) {
                log.warn("auth.session.degraded uid={} path={}", parsed.uid(), request.getRequestURI());
                return true;
            }
            return reject(response, 503, "依赖服务不可用，请稍后重试");
        }
        if (status == SessionStatus.ABSENT) {
            return optional || reject(response, 401, "登录已失效，请重新登录");
        }

        Optional<UserAccount> account = userQueryService.findAccountById(parsed.uid());
        if (account.isEmpty() || account.get().status() == null || account.get().status() != 1) {
            return optional || reject(response, 401, "账号不可用");
        }

        CurrentUserContext.set(new ReqInfo(parsed.uid(), account.get().role(), parsed.jti()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private boolean isOptional(Object handler) {
        if (handler instanceof HandlerMethod method) {
            return method.hasMethodAnnotation(OptionalAuth.class)
                    || method.getBeanType().isAnnotationPresent(OptionalAuth.class);
        }
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith(BEARER) ? header.substring(BEARER.length()).trim() : header.trim();
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ResVo.fail(code, message));
        return false;
    }
}
