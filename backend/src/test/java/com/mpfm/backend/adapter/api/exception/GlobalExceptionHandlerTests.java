package com.mpfm.backend.adapter.api.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.common.security.WebDavUserCacheService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TestExceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, RequestCorrelationFilter.class, SecurityEventLogger.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "security.jwt.issuer=test",
        "security.jwt.access-token-expire-seconds=600",
        "security.jwt.refresh-token-expire-seconds=3600",
        "security.jwt.signing-key=0123456789abcdef0123456789abcdef"
})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private WebDavUserCacheService webDavUserCacheService;

    @Test
    @WithMockUser(username = "u1", roles = {"USER"})
    void shouldMapBusinessException() throws Exception {
        mockMvc.perform(get("/api/v1/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @WithMockUser(username = "u1", roles = {"USER"})
    void shouldMapValidationException() throws Exception {
        mockMvc.perform(get("/api/v1/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "u1", roles = {"USER"})
    void shouldMapAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
}

