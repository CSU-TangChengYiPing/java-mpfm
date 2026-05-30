package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.application.user.UserManagementService;
import com.mpfm.backend.application.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理控制器，提供创建、更新、停用与重置凭证等高权限用户操作接口。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private static final String ADMIN_OR_ROOT = "hasRole('ADMIN') or hasRole('ROOT')";

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize(ADMIN_OR_ROOT)
    public UserItemsResponse list(@RequestParam(name = "q", required = false) String q,
                                  @RequestParam(name = "page", defaultValue = "1") int page,
                                  @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                  Principal principal) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(200, pageSize));
        java.util.List<UserSummaryResponse> all = userManagementService.search(principal.getName(), q, q, null).stream()
                .map(UserSummaryResponse::from)
                .toList();
        int total = all.size();
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);
        java.util.List<UserSummaryResponse> items = all.subList(from, to);
        return new UserItemsResponse(items, new PageMeta(safePage, safePageSize, total));
    }

    @PostMapping
    @PreAuthorize(ADMIN_OR_ROOT)
    public UserSummaryResponse create(@RequestBody CreateUserRequest request, Principal principal) {
        UserManagementService.UserSummary created = userManagementService.adminCreateUser(
                principal.getName(),
                request.username(),
                request.password(),
                request.displayName(),
                request.email(),
                request.phone(),
                PlatformRole.valueOf(request.role().toUpperCase(Locale.ROOT)),
                request.qosProfile()
        );
        return UserSummaryResponse.from(created);
    }

    @PutMapping("/{userId}")
    @PreAuthorize(ADMIN_OR_ROOT)
    public UserSummaryResponse update(@PathVariable UUID userId, @RequestBody UpdateUserRequest request, Principal principal) {
        UserManagementService.UserSummary updated = userManagementService.adminUpdateUser(
                principal.getName(),
                userId,
                request.displayName(),
                request.email(),
                request.phone(),
                PlatformRole.valueOf(request.role().toUpperCase(Locale.ROOT)),
                UserStatus.valueOf(request.status().toUpperCase(Locale.ROOT)),
                request.qosProfile(),
                request.customUploadBps(),
                request.customDownloadBps(),
                request.qosCustomEnabled(),
                request.uploadPaused(),
                request.downloadPaused()
        );
        return UserSummaryResponse.from(updated);
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize(ADMIN_OR_ROOT)
    public UserSummaryResponse disable(@PathVariable UUID userId, Principal principal) {
        UserManagementService.UserSummary disabled = userManagementService.adminDisableUser(principal.getName(), userId);
        return UserSummaryResponse.from(disabled);
    }

    @PostMapping("/{userId}/reset-credential")
    @PreAuthorize(ADMIN_OR_ROOT)
    public OperationResponse resetCredential(@PathVariable UUID userId, @RequestBody ResetCredentialRequest request, Principal principal) {
        userManagementService.adminResetCredential(principal.getName(), userId, request.newCredential());
        return new OperationResponse("reset_credential", "success");
    }

    /** 管理员创建用户请求，包含初始凭证、展示信息与目标平台角色。 */
    public record CreateUserRequest(@NotBlank String username, @NotBlank String password, @NotBlank String displayName,
                                    String email, String phone, @NotBlank String role, String qosProfile) {
    }

    /** 管理员更新用户请求，允许修改资料字段、角色与账号状态。 */
    public record UpdateUserRequest(@NotBlank String displayName, String email, String phone,
                                    @NotBlank String role, @NotBlank String status, String qosProfile,
                                    Long customUploadBps, Long customDownloadBps,
                                    Boolean qosCustomEnabled,
                                    Boolean uploadPaused, Boolean downloadPaused) {
    }

    /** 重置凭证请求，`newCredential` 为管理员下发的新登录凭证。 */
    public record ResetCredentialRequest(@NotBlank String newCredential) {
    }

    /** 用户摘要响应，返回管理页列表渲染所需的核心用户字段。 */
    public record UserSummaryResponse(String userId, String username, String displayName, String role, String status, String qosProfile,
                                      long customUploadBps, long customDownloadBps, boolean qosCustomEnabled,
                                      boolean uploadPaused, boolean downloadPaused) {
        static UserSummaryResponse from(UserManagementService.UserSummary user) {
            return new UserSummaryResponse(
                    user.userId().toString(),
                    user.username(),
                    user.displayName(),
                    user.role(),
                    user.status(),
                    user.qosProfile(),
                    user.customUploadBps(),
                    user.customDownloadBps(),
                    user.qosCustomEnabled(),
                    user.uploadPaused(),
                    user.downloadPaused()
            );
        }
    }

    /** 通用操作响应，返回动作名称与执行状态。 */
    public record OperationResponse(String action, String status) {
    }

    /** 用户分页响应，提供列表项与分页元信息。 */
    public record UserItemsResponse(java.util.List<UserSummaryResponse> items, PageMeta page) {
    }

    /** 分页元信息。 */
    public record PageMeta(int page, int pageSize, int total) {
    }
}




