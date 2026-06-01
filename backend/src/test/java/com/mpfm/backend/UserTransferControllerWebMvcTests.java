package com.mpfm.backend;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.auth.UserTransferController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.WebDavUserCacheService;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserTransferController.class)
@Import({
        SecurityConfig.class,
        RequestCorrelationFilter.class,
        SecurityEventLogger.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "security.jwt.issuer=test",
        "security.jwt.access-token-expire-seconds=600",
        "security.jwt.refresh-token-expire-seconds=3600",
        "security.jwt.signing-key=0123456789abcdef0123456789abcdef"
})
class UserTransferControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private WebDavUserCacheService webDavUserCacheService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private TransferTelemetryService transferTelemetryService;

    @Test
    void shouldReturnCurrentUserRate() throws Exception {
        given(transferTelemetryService.forCurrentUser("alice"))
                .willReturn(new TransferTelemetryService.TransferSnapshot("alice", 3072, 1024, 1, 1, 2));

        mockMvc.perform(get("/api/v4/transfers/me/rates").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadBps").value(3072))
                .andExpect(jsonPath("$.downloadBps").value(1024));
    }
}
