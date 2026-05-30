package com.mpfm.backend;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.admin.AdminQosController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.security.QosPolicyService;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminQosController.class)
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
class AdminQosControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private QosPolicyService qosPolicyService;

    @Test
    void shouldRejectNonRootUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qos/policies").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void shouldReturnPoliciesForRoot() throws Exception {
        given(qosPolicyService.listPolicies()).willReturn(List.of(
                new QosPolicyService.QosPolicy("default", "默认", 100, 100, 1, 1, true, "root")));
        mockMvc.perform(get("/api/v1/admin/qos/policies").with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("default"));
    }
}

