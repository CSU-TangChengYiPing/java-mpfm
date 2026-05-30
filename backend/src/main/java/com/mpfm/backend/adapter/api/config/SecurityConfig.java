package com.mpfm.backend.adapter.api.config;

import com.mpfm.backend.common.security.JwtAuthenticationFilter;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.common.security.WebDavBasicAuthenticationFilter;
import com.mpfm.backend.common.security.WebDavUserCacheService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置类，负责鉴权链路、访问控制与认证组件装配。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final WebDavUserCacheService webDavUserCacheService;
    private final TransferUploadRateLimitFilter transferUploadRateLimitFilter;
    private final boolean scalarEnabled;
    private final String scalarPath;
    private final String apiDocsPath;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          JwtTokenService jwtTokenService,
                          UserRepository userRepository,
                          WebDavUserCacheService webDavUserCacheService,
                          TransferUploadRateLimitFilter transferUploadRateLimitFilter,
                          @Value("${scalar.enabled:false}") boolean scalarEnabled,
                          @Value("${scalar.path:/scalar}") String scalarPath,
                          @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.webDavUserCacheService = webDavUserCacheService;
        this.transferUploadRateLimitFilter = transferUploadRateLimitFilter;
        this.scalarEnabled = scalarEnabled;
        this.scalarPath = scalarPath;
        this.apiDocsPath = apiDocsPath;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenService, userRepository);
        WebDavBasicAuthenticationFilter webDavBasicFilter = new WebDavBasicAuthenticationFilter(webDavUserCacheService, passwordEncoder());
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/system/ping").permitAll()
                .requestMatchers("/api/v1/system/dev-cert").permitAll()
                .requestMatchers("/api/v1/users/avatar/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/dav", "/dav/**").permitAll()
                .requestMatchers("/error").permitAll()
                // 仅在启用 Scalar 的环境放行文档入口，避免生产环境默认暴露 API 看板。
                .requestMatchers(documentationMatchers()).permitAll()
                .anyRequest().authenticated())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .addFilterBefore(webDavBasicFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtFilter, WebDavBasicAuthenticationFilter.class)
            .addFilterAfter(transferUploadRateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getPlatformRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    /**
     * WebDAV 使用扩展 HTTP 方法，需显式放开防火墙方法白名单，避免 PROPFIND/MKCOL 等被 400 拦截。
     */
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of(
                "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS",
                "PROPFIND", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK"));
        return firewall;
    }

    private String[] documentationMatchers() {
        if (!scalarEnabled) {
            return new String[0];
        }
        List<String> matchers = new ArrayList<>();
        matchers.add(normalizePath(scalarPath));
        matchers.add(normalizePath(scalarPath) + "/**");
        matchers.add(normalizePath(apiDocsPath));
        matchers.add(normalizePath(apiDocsPath) + "/**");
        // 兼容默认 springdoc 路径，避免环境变量切换后文档页 401。
        matchers.add("/v3/api-docs");
        matchers.add("/v3/api-docs/**");
        matchers.add("/api-docs");
        matchers.add("/api-docs/**");
        return matchers.toArray(String[]::new);
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        return rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    }
}




