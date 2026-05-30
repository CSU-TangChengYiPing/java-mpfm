package com.mpfm.backend.common.security;

import java.time.Instant;

/**
 * JwtPrincipal JWT 主体模型，承载认证后的用户身份信息。
 */
public record JwtPrincipal(String subject, String role, String type, Instant issuedAt, int credentialVersion) {
}





