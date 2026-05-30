package com.mpfm.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 令牌服务，负责签发访问/刷新令牌并解析令牌载荷为统一身份主体。
 */
@Component
public final class JwtTokenService {

    private final SecretKey secretKey;
    private final String issuer;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public JwtTokenService(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.signingKey().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.accessExpireSeconds = properties.accessTokenExpireSeconds();
        this.refreshExpireSeconds = properties.refreshTokenExpireSeconds();
    }

    public String issueAccessToken(String subject, String role, int credentialVersion) {
        return issue(subject, role, "access", accessExpireSeconds, credentialVersion);
    }

    public String issueRefreshToken(String subject, String role, int credentialVersion) {
        return issue(subject, role, "refresh", refreshExpireSeconds, credentialVersion);
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return new JwtPrincipal(
                claims.getSubject(),
                claims.get("role", String.class),
                claims.get("type", String.class),
                claims.getIssuedAt().toInstant(),
                claims.get("cv", Integer.class)
        );
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    private String issue(String subject, String role, String type, long expireSeconds, int credentialVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .claim("role", role)
                .claim("type", type)
                .claim("cv", credentialVersion)
                .signWith(secretKey)
                .compact();
    }
}





