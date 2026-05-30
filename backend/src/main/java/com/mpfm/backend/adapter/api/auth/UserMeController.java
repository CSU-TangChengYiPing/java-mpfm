package com.mpfm.backend.adapter.api.auth;

import com.mpfm.backend.application.user.AuthApplicationService;
import com.mpfm.backend.application.user.UserManagementService;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户（Me）接口控制器，负责个人资料、偏好、凭证与会话自助管理.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserMeController {

    /** 认证应用服务，用于会话查询与会话撤销. */
    private final AuthApplicationService authApplicationService;
    /** 用户管理服务，用于个人资料与凭证更新. */
    private final UserManagementService userManagementService;

    /**
     * 构造当前用户控制器并注入所需应用服务.
     *
     * @param authApplicationService 认证应用服务
     * @param userManagementService 用户管理服务
     */
    public UserMeController(AuthApplicationService authApplicationService, UserManagementService userManagementService) {
        this.authApplicationService = authApplicationService;
        this.userManagementService = userManagementService;
    }

    /**
     * 获取当前登录用户资料.
     *
     * @param principal 当前登录主体
     * @return 用户详情响应
     */
    @GetMapping
    public MeResponse me(Principal principal) {
        return MeResponse.from(userManagementService.me(principal.getName()));
    }

    /**
     * 更新当前用户个人资料.
     *
     * @param request 资料更新请求
     * @param principal 当前登录主体
     * @return 更新后的用户详情
     */
    @PutMapping("/profile")
    public MeResponse updateProfile(@RequestBody UpdateProfileRequest request, Principal principal) {
        return MeResponse.from(userManagementService.updateProfile(
                principal.getName(), request.displayName(), request.email(), request.phone()));
    }

    /**
     * 更新当前用户头像地址.
     *
     * @param request 头像更新请求
     * @param principal 当前登录主体
     * @return 更新后的用户详情
     */
    @PutMapping("/avatar")
    public MeResponse updateAvatar(@RequestBody UpdateAvatarRequest request, Principal principal) {
        return MeResponse.from(userManagementService.updateAvatar(principal.getName(), request.avatarUrl()));
    }

    /**
     * 更新当前用户偏好设置.
     *
     * @param request 偏好更新请求
     * @param principal 当前登录主体
     * @return 更新后的用户详情
     */
    @PutMapping("/preferences")
    public MeResponse updatePreferences(@RequestBody UpdatePreferencesRequest request, Principal principal) {
        return MeResponse.from(userManagementService.updatePreferences(
                principal.getName(), request.language(), request.fileViewMode()));
    }

    /**
     * 修改当前用户登录凭证，并按策略撤销历史会话.
     *
     * @param request 凭证变更请求
     * @param principal 当前登录主体
     * @return 操作状态与撤销会话数量
     */
    @PostMapping("/change-credential")
    public OperationResponse changeCredential(@RequestBody ChangeCredentialRequest request, Principal principal) {
        int revokedSessions = userManagementService.changeCredential(
                principal.getName(),
                request.oldCredential(),
                request.newCredential()
        );
        return new OperationResponse("change_credential", "success", revokedSessions);
    }

    /**
     * 按条件检索当前用户可见用户列表.
     *
     * @param username 用户名过滤条件
     * @param displayName 展示名过滤条件
     * @param status 状态过滤条件
     * @param principal 当前登录主体
     * @return 用户摘要列表
     */
    @GetMapping("/search")
    public List<UserSummaryResponse> search(@RequestParam(required = false) String username,
                                            @RequestParam(required = false) String displayName,
                                            @RequestParam(required = false) String status,
                                            Principal principal) {
        return userManagementService.search(principal.getName(), username, displayName, status).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    /**
     * 获取当前用户全部会话信息.
     *
     * @param principal 当前登录主体
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public List<SessionItem> sessions(Principal principal) {
        return authApplicationService.sessions(principal.getName()).stream()
                .map(item -> new SessionItem(
                        item.sessionId().toString(),
                        item.status(),
                        item.expiresAt(),
                        item.clientIp(),
                        item.userAgent(),
                        item.deviceLabel()))
                .toList();
    }

    /**
     * 撤销指定会话.
     *
     * @param sessionId 目标会话标识
     * @param principal 当前登录主体
     * @return 操作结果
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    public OperationResponse revoke(@PathVariable UUID sessionId, Principal principal) {
        authApplicationService.revokeSession(principal.getName(), sessionId);
        return new OperationResponse("revoke_session", "success", 1);
    }

    /**
     * 当前用户详情响应，聚合账号资料、展示配置与账号状态.
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param displayName 展示名
     * @param email 邮箱
     * @param phone 电话
     * @param avatarUrl 头像地址
     * @param language 语言偏好
     * @param fileViewMode 文件视图模式
     * @param qosProfile QoS 策略档位
     * @param role 平台角色
     * @param status 账号状态
     */
    public record MeResponse(String userId, String username, String displayName, String email, String phone,
                             String avatarUrl, String language, String fileViewMode, String qosProfile, String role, String status) {
        static MeResponse from(UserManagementService.MeResult me) {
            return new MeResponse(
                    me.userId().toString(), me.username(), me.displayName(), me.email(), me.phone(),
                    me.avatarUrl(), me.language(), me.fileViewMode(), me.qosProfile(), me.role(), me.status()
            );
        }
    }
    /**
     * 用户检索结果摘要响应，返回列表展示所需最小字段.
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param displayName 展示名
     * @param role 平台角色
     * @param status 账号状态
     * @param qosProfile QoS 策略档位
     */
    public record UserSummaryResponse(String userId, String username, String displayName, String avatarUrl, String role, String status, String qosProfile) {
        static UserSummaryResponse from(UserManagementService.UserSummary user) {
            return new UserSummaryResponse(
                    user.userId().toString(),
                    user.username(),
                    user.displayName(),
                    user.avatarUrl(),
                    user.role(),
                    user.status(),
                    user.qosProfile()
            );
        }
    }
    /**
     * 个人资料更新请求体，displayName 必填，email/phone 按需更新.
     *
     * @param displayName 展示名
     * @param email 邮箱
     * @param phone 电话
     */
    public record UpdateProfileRequest(@NotBlank String displayName, String email, String phone) { }
    /**
     * 头像更新请求体，avatarUrl 必填.
     *
     * @param avatarUrl 头像地址
     */
    public record UpdateAvatarRequest(@NotBlank String avatarUrl) { }
    /**
     * 偏好设置更新请求体，包含语言与文件视图模式.
     *
     * @param language 语言偏好
     * @param fileViewMode 文件视图模式
     */
    public record UpdatePreferencesRequest(@NotBlank String language, @NotBlank String fileViewMode) { }
    /**
     * 凭证变更请求体，要求旧凭证、新凭证与验证码四项全部提供.
     *
     * @param oldCredential 旧凭证
     * @param newCredential 新凭证
     * @param captchaId 验证码标识
     * @param captchaAnswer 验证码答案
     */
    public record ChangeCredentialRequest(@NotBlank String oldCredential,
                                          @NotBlank String newCredential) { }
    /**
     * 会话列表项响应，描述单个会话的状态与过期时间.
     *
     * @param sessionId 会话标识
     * @param status 会话状态
     * @param expiresAt 会话过期时间
     */
    public record SessionItem(String sessionId, String status, String expiresAt,
                              String clientIp, String userAgent, String deviceLabel) { }
    /**
     * 操作结果响应，返回动作状态及本次吊销会话数量.
     *
     * @param action 操作名称
     * @param status 操作状态
     * @param revokedSessions 吊销会话数量
     */
    public record OperationResponse(String action, String status, int revokedSessions) { }
}


