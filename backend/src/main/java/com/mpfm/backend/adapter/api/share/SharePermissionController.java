package com.mpfm.backend.adapter.api.share;

import com.mpfm.backend.application.share.ShareApplicationService;
import com.mpfm.backend.application.share.SharePermissionService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 共享权限查询控制器，提供指定挂载与路径的有效权限计算结果查询接口。
 */
@RestController
@RequestMapping("/api/v1")
public class SharePermissionController {

    private final ShareApplicationService service;
    private final SharePermissionService sharePermissionService;

    public SharePermissionController(ShareApplicationService service, SharePermissionService sharePermissionService) {
        this.service = service;
        this.sharePermissionService = sharePermissionService;
    }

    @GetMapping("/permissions/effective")
    public ShareApiModels.PermissionResponse effective(@RequestParam UUID mountId,
                                                       @RequestParam String path,
                                                       Principal p) {
        return ShareApiModels.PermissionResponse.from(service.effective(p.getName(), mountId, path));
    }

    @GetMapping("/mounts/{mountId}/share-audits")
    public List<ShareApiModels.AuditResponse> listAudits(@PathVariable UUID mountId, Principal p) {
        return sharePermissionService.listAudits(p.getName(), mountId).stream().map(ShareApiModels.AuditResponse::from).toList();
    }

    @GetMapping("/mounts/{mountId}/share-grants")
    public List<ShareApiModels.GrantResponse> listGrants(@PathVariable UUID mountId, Principal p) {
        return sharePermissionService.listGrants(p.getName(), mountId).stream().map(ShareApiModels.GrantResponse::from).toList();
    }

    @PostMapping("/mounts/{mountId}/share-grants")
    public ShareApiModels.GrantResponse upsertGrant(@PathVariable UUID mountId,
                                                    @RequestBody ShareApiModels.UpsertGrantRequest req,
                                                    Principal p) {
        return ShareApiModels.GrantResponse.from(
                sharePermissionService.upsertGrant(p.getName(), mountId, req.role(), req.pathScopes(), req.permissions()));
    }

    @DeleteMapping("/mounts/{mountId}/share-grants/{grantId}")
    public ShareApiModels.OperationResponse deleteGrant(@PathVariable UUID mountId,
                                                        @PathVariable UUID grantId,
                                                        Principal p) {
        sharePermissionService.deleteGrant(p.getName(), mountId, grantId);
        return new ShareApiModels.OperationResponse("delete_share_grant", "success");
    }

    @GetMapping("/mounts/{mountId}/permissions/preview")
    public List<ShareApiModels.PreviewNodeResponse> preview(@PathVariable UUID mountId,
                                                            @RequestParam String roleId,
                                                            @RequestParam String path,
                                                            @RequestParam(required = false) Integer maxDepth,
                                                            Principal p) {
        return sharePermissionService.preview(p.getName(), mountId, roleId, path, maxDepth).stream()
                .map(ShareApiModels.PreviewNodeResponse::from)
                .toList();
    }
}




