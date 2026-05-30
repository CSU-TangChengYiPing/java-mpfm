package com.mpfm.backend.common.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性模型，绑定签发方、访问/刷新令牌有效期与签名密钥配置项。
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @Min(1) long accessTokenExpireSeconds,
        @Min(1) long refreshTokenExpireSeconds,
        @NotBlank String signingKey
) {
}





