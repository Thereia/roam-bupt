package com.roambupt.web.global;

import com.roambupt.core.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析。
 *
 * <p>默认不信任 X-Forwarded-For——否则客户端自己写一个头就能绕过限流。
 * 只有显式开启 trustForwardedFor，并且（若配置了）来源在可信代理列表里才采信。</p>
 */
@Component
public class ClientIpResolver {

    private final SecurityProperties properties;

    public ClientIpResolver(SecurityProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!properties.trustForwardedFor()) {
            return remote;
        }
        var trusted = properties.trustedProxies();
        if (trusted != null && !trusted.isEmpty() && !trusted.contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        // 取最左侧的客户端地址；右侧是各级代理，不可信
        return forwarded.split(",")[0].trim();
    }
}
