package com.mpfm.backend.adapter.api.auth;

import com.mpfm.backend.application.user.AuthApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证领域 HTTP 入口控制器，负责注册、登录、验证码、令牌刷新与会话管理编排.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 认证应用服务，承载注册登录与会话生命周期业务. */
    private final AuthApplicationService authApplicationService;

    /**
     * 构造认证控制器并注入认证应用服务.
     *
     * @param authApplicationService 认证应用服务
     */
    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 注册新用户并返回登录态令牌.
     *
     * @param request 注册请求体
     * @return 认证成功响应
     */
    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return AuthResponse.from(authApplicationService.register(
                new AuthApplicationService.RegisterCommand(
                        request.username(), request.password(), request.displayName(), request.email(), request.phone(),
                        request.captchaId(), request.captchaAnswer()),
                extractClientContext(servletRequest)));
    }

    /**
     * 使用用户名密码执行登录，并返回访问令牌与会话标识.
     *
     * @param request 登录请求体
     * @return 认证成功响应
     */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return AuthResponse.from(authApplicationService.login(
                new AuthApplicationService.LoginCommand(request.username(), request.password(), request.captchaId(), request.captchaAnswer()),
                extractClientContext(servletRequest)));
    }

    /**
     * 签发验证码，未指定场景时默认使用登录场景.
     *
     * @param request 验证码请求体，可为空
     * @return 验证码响应
     */
    @PostMapping("/captcha")
    public CaptchaResponse captcha(@RequestBody(required = false) CaptchaRequest request) {
        String scene = request == null ? "login" : request.scene();
        return CaptchaResponse.from(authApplicationService.issueCaptcha(scene));
    }

    /**
     * 使用刷新令牌换取新的访问令牌.
     *
     * @param request 刷新请求体
     * @return 认证成功响应
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return AuthResponse.from(authApplicationService.refresh(request.refreshToken(), request.sessionId(), extractClientContext(servletRequest)));
    }

    /**
     * 注销指定会话并使刷新令牌失效.
     *
     * @param request 注销请求体
     * @return 操作状态响应
     */
    @PostMapping("/logout")
    public OperationResponse logout(@RequestBody RefreshRequest request) {
        authApplicationService.logout(request.refreshToken(), request.sessionId());
        return new OperationResponse("logout", "success");
    }

    /**
     * 查询当前登录用户对应会话状态.
     *
     * @param principal 当前登录主体
     * @return 当前会话响应
     */
    @GetMapping("/session")
    public SessionResponse session(Principal principal) {
        AuthApplicationService.SessionInfo info = authApplicationService.currentSession(principal.getName());
        return new SessionResponse(new User(info.username(), info.role()), info.status());
    }

    /**
     * 列出当前用户全部历史会话.
     *
     * @param principal 当前登录主体
     * @return 会话列表响应
     */
    @GetMapping("/sessions")
    public List<UserSessionResponse> sessions(Principal principal) {
        return authApplicationService.sessions(principal.getName()).stream()
                .map(item -> new UserSessionResponse(
                        item.sessionId().toString(),
                        item.status(),
                        item.expiresAt(),
                        item.clientIp(),
                        item.userAgent(),
                        item.deviceLabel()))
                .toList();
    }

    private AuthApplicationService.ClientContext extractClientContext(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = (forwardedFor == null || forwardedFor.isBlank())
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        String userAgent = request.getHeader("User-Agent");
        return new AuthApplicationService.ClientContext(
                (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp,
                (userAgent == null || userAgent.isBlank()) ? "unknown" : userAgent);
    }

    /**
     * 撤销指定会话，常用于异常登录处置.
     *
     * @param sessionId 目标会话标识
     * @param principal 当前登录主体
     * @return 操作状态响应
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    public OperationResponse revoke(@PathVariable UUID sessionId, Principal principal) {
        authApplicationService.revokeSession(principal.getName(), sessionId);
        return new OperationResponse("revoke_session", "success");
    }

    /**
     * 注册请求体，包含账号基础信息与可选验证码参数.
     *
     * @param username 登录用户名
     * @param password 登录密码
     * @param displayName 用户展示名
     * @param email 联系邮箱
     * @param phone 联系电话
     * @param captchaId 验证码标识
     * @param captchaAnswer 验证码答案
     */
    public record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotBlank String displayName, String email, String phone,
                                  String captchaId, String captchaAnswer) { }
    /**
     * 登录请求体，用户名密码必填，验证码按风控策略按需启用.
     *
     * @param username 登录用户名
     * @param password 登录密码
     * @param captchaId 验证码标识
     * @param captchaAnswer 验证码答案
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password, String captchaId, String captchaAnswer) { }
    /**
     * 验证码签发请求体；未传 scene 时默认按登录场景处理.
     *
     * @param scene 验证码业务场景
     */
    public record CaptchaRequest(String scene) { }
    /**
     * 刷新/登出请求体，要求提供 refreshToken 与目标会话标识.
     *
     * @param refreshToken 刷新令牌
     * @param sessionId 会话标识
     */
    public record RefreshRequest(@NotBlank String refreshToken, @NotNull UUID sessionId) { }
    /**
     * 通用操作结果响应，返回动作名称与执行状态.
     *
     * @param action 操作名称
     * @param status 操作状态
     */
    public record OperationResponse(String action, String status) { }

    /**
     * 认证成功响应，包含令牌组与当前用户摘要信息.
     *
     * @param token 令牌信息
     * @param user 用户摘要
     */
    public record AuthResponse(Token token, User user) {
        static AuthResponse from(AuthApplicationService.AuthResult result) {
            return new AuthResponse(new Token("Bearer", result.accessToken(), result.refreshToken(), result.sessionId().toString()), new User(result.username(), result.role()));
        }
    }

    /**
     * 当前会话状态响应，用于展示登录用户与会话有效状态.
     *
     * @param user 用户摘要
     * @param status 会话状态
     */
    public record SessionResponse(User user, String status) { }
    /**
     * 会话列表项响应，包含会话标识、状态与到期时间.
     *
     * @param sessionId 会话标识
     * @param status 会话状态
     * @param expiresAt 会话过期时间
     */
    public record UserSessionResponse(String sessionId, String status, String expiresAt,
                                      String clientIp, String userAgent, String deviceLabel) { }
    /**
     * 令牌信息载体，包含访问令牌、刷新令牌及关联会话标识.
     *
     * @param tokenType 令牌类型
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌
     * @param sessionId 会话标识
     */
    public record Token(String tokenType, String accessToken, String refreshToken, String sessionId) { }
    /**
     * 用户摘要信息，供认证相关响应复用.
     *
     * @param username 用户名
     * @param role 平台角色
     */
    public record User(String username, String role) { }
    /**
     * 验证码签发响应，返回验证码标识、过期时间与展示内容.
     *
     * @param captchaId 验证码标识
     * @param scene 验证码场景
     * @param message 验证码提示信息
     * @param expiresInSeconds 过期秒数
     * @param imageDataUrl 验证码图片内容
     */
    public record CaptchaResponse(String captchaId, String scene, String message, long expiresInSeconds, String imageDataUrl) {
        static CaptchaResponse from(com.mpfm.backend.application.user.CaptchaService.CaptchaIssue issue) {
            return new CaptchaResponse(issue.captchaId(), issue.scene(), issue.message(), issue.expiresInSeconds(), issue.imageDataUrl());
        }
    }
}


