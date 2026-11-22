package com.roambupt.web.global;

import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.core.limit.WriteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 写操作限流：只拦 POST / PUT / DELETE，GET 不受影响。
 *
 * <p>已登录按 uid 计数，未登录按 IP 计数；登录/注册有自己的限流，这里跳过。</p>
 */
@Component
public class WriteRateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final Set<String> SKIP_PREFIXES = Set.of("/api/v1/auth/");

    private final WriteRateLimiter limiter;
    private final ClientIpResolver clientIpResolver;

    public WriteRateLimitInterceptor(WriteRateLimiter limiter, ClientIpResolver clientIpResolver) {
        this.limiter = limiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (SKIP_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }

        Long uid = CurrentUserContext.uid();
        String key = uid != null ? "u:" + uid : "ip:" + clientIpResolver.resolve(request);
        if (limiter.tryAcquire(key)) {
            return true;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":429,\"message\":\"操作太频繁，请稍后再试\",\"data\":null}");
        return false;
    }
}
