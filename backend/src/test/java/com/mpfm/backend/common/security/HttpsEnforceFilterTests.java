package com.mpfm.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpsEnforceFilterTests {

    @Test
    void shouldRedirectWhenForceEnabledAndRequestNotSecure() throws ServletException, IOException {
        HttpsEnforceFilter filter = new HttpsEnforceFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/files/list");
        request.addHeader("Host", "localhost:8080");
        request.setQueryString("mountId=a&path=./personal/a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedChain = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> reachedChain.set(true));

        assertThat(response.getStatus()).isEqualTo(308);
        assertThat(response.getHeader("Location"))
                .isEqualTo("https://localhost:8080/api/v1/files/list?mountId=a&path=./personal/a");
        assertThat(reachedChain.get()).isFalse();
    }

    @Test
    void shouldPassThroughWhenSecure() throws ServletException, IOException {
        HttpsEnforceFilter filter = new HttpsEnforceFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/ping");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}


