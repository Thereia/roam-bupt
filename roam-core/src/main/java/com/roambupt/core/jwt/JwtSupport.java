package com.roambupt.core.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256）。
 * 只放身份（uid / username / jti），权限由每次请求查库决定。
 */
public class JwtSupport {

    private final SecretKey key;
    private final Duration ttl;

    public JwtSupport(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.ttl();
    }

    public IssuedToken issue(long uid, String username) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(Long.toString(uid))
                .id(jti)
                .claim("uid", uid)
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    public ParsedToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedToken(((Number) claims.get("uid")).longValue(), claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("token 无效", e);
        }
    }
}
