package com.mpfm.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.BDDMockito.given;

import com.mpfm.backend.adapter.api.admin.AdminLogsController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.audit.BackendLogReadService;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.WebDavUserCacheService;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminLogsController.class)
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
class AdminLogsControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private WebDavUserCacheService webDavUserCacheService;
    @MockitoBean
    private BackendLogReadService backendLogReadService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;

    @Test
    void shouldRejectNonRootUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/logs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void shouldReturnValidationErrorWhenMaxLinesOutOfRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/logs")
                        .with(user("root").roles("ROOT"))
                        .param("max_lines", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnLogsForRoot() throws Exception {
        Path logPath = Path.of("./logs/mpfm-backend.log").toAbsolutePath().normalize();
        given(backendLogReadService.resolveLogPath(null)).willReturn(java.util.Optional.of(logPath));
        given(backendLogReadService.logExists(logPath)).willReturn(true);
        given(backendLogReadService.readTailLines(logPath, 10)).willReturn(java.util.List.of("line-1", "line-2"));
        mockMvc.perform(get("/api/v1/admin/logs")
                        .with(user("root").roles("ROOT"))
                        .param("max_lines", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_lines").value(10))
                .andExpect(jsonPath("$.log_file").exists())
                .andExpect(jsonPath("$.content").value("line-1\nline-2"))
                .andExpect(jsonPath("$.lines").isArray());
    }
}
