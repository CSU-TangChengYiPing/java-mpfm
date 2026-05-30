package com.mpfm.backend.adapter.api.share;

import com.mpfm.backend.application.share.ShareApplicationService;
import com.mpfm.backend.common.audit.AuditAction;
import com.mpfm.backend.common.audit.AuditEvent;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 共享角色控制器，提供角色生命周期管理与路径策略维护接口。
 */
@RestController
@RequestMapping("/api/v1")
public class ShareRoleController {
    private static final String RISK_HIGH = "high";
    private static final String TARGET_SHARE_ROLE = "share_role";

    private final ShareApplicationService service;

    public ShareRoleController(ShareApplicationService service) {
        this.service = service;
    }

    @PostMapping("/mounts/{mountId}/share-roles")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_role_create", target = TARGET_SHARE_ROLE, riskLevel = RISK_HIGH)
    public ShareApiModels.RoleResponse createRole(@PathVariable UUID mountId,
                                                  @RequestBody ShareApiModels.CreateRoleRequest req,
                                                  Principal p) {
        return ShareApiModels.RoleResponse.from(service.createRole(p.getName(), mountId, req.name(), req.roleExpiresAt()));
    }

    @GetMapping("/mounts/{mountId}/share-roles")
    public List<ShareApiModels.RoleResponse> listRoles(@PathVariable UUID mountId, Principal p) {
        return service.listRoles(p.getName(), mountId).stream().map(ShareApiModels.RoleResponse::from).toList();
    }

    @PutMapping("/share-roles/{roleId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_role_update", target = TARGET_SHARE_ROLE, riskLevel = RISK_HIGH)
    public ShareApiModels.RoleResponse updateRole(@PathVariable UUID roleId,
                                                  @RequestBody ShareApiModels.UpdateRoleRequest req,
                                                  Principal p) {
        return ShareApiModels.RoleResponse.from(service.updateRole(p.getName(), roleId, req.name(), req.roleExpiresAt()));
    }

    @PostMapping("/share-roles/{roleId}/disable")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_role_disable", target = TARGET_SHARE_ROLE, riskLevel = RISK_HIGH)
    public ShareApiModels.RoleResponse disableRole(@PathVariable UUID roleId, Principal p) {
        return ShareApiModels.RoleResponse.from(service.disableRole(p.getName(), roleId));
    }

    @DeleteMapping("/share-roles/{roleId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_role_delete", target = TARGET_SHARE_ROLE, riskLevel = RISK_HIGH)
    public ShareApiModels.OperationResponse deleteRole(@PathVariable UUID roleId, Principal p) {
        service.deleteRole(p.getName(), roleId);
        return new ShareApiModels.OperationResponse("delete_share_role", "success");
    }

    @PutMapping("/share-roles/{roleId}/path-policies")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_policy_update", target = "share_role_policy", riskLevel = RISK_HIGH)
    public List<ShareApiModels.PolicyResponse> updatePolicy(@PathVariable UUID roleId,
                                                             @RequestBody ShareApiModels.UpdatePolicyRequest req,
                                                             Principal p) {
        List<ShareApplicationService.PathPolicyCommand> items = req.items().stream()
                .map(i -> new ShareApplicationService.PathPolicyCommand(i.pathPattern(), i.canVisible(), i.canRead(), i.canWrite()))
                .toList();
        return service.updatePolicies(p.getName(), roleId, items).stream().map(ShareApiModels.PolicyResponse::from).toList();
    }
}




