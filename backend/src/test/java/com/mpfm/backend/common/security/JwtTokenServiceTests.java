package com.mpfm.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtTokenServiceTests {

    private final JwtTokenService service = new JwtTokenService(
            new JwtProperties("mpfm-test", 600, 3600, "01234567890123456789012345678901")
    );

    @Test
    void shouldIssueAndParseAccessToken() {
        String token = service.issueAccessToken("u-1", "admin", 1);
        JwtPrincipal principal = service.parse(token);
        assertThat(principal.subject()).isEqualTo("u-1");
        assertThat(principal.role()).isEqualTo("admin");
        assertThat(principal.type()).isEqualTo("access");
        assertThat(principal.credentialVersion()).isEqualTo(1);
    }

    @Test
    void shouldIssueRefreshToken() {
        String token = service.issueRefreshToken("u-1", "admin", 1);
        JwtPrincipal principal = service.parse(token);
        assertThat(principal.type()).isEqualTo("refresh");
    }
}

