package com.mpfm.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class WebDavBasicAuthenticationFilterTests {

    private WebDavUserCacheService userCacheService;
    private PasswordEncoder passwordEncoder;
    private WebDavBasicAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userCacheService = mock(WebDavUserCacheService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        filter = new WebDavBasicAuthenticationFilter(userCacheService, passwordEncoder);
    }

    @Test
    void davBearerAuthShouldPassThroughToNextFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/demo");
        request.addHeader("Authorization", "Bearer test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(userCacheService);
        verifyNoInteractions(passwordEncoder);
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void davMissingAuthShouldReturnBasicChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, Mockito.never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"mpfm-webdav\"");
    }
}
