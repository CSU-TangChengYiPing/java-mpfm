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
 * 共享链接控制器，提供链接创建、更新、撤销、删除、解析与角色切换接口。
 */
@RestController
@RequestMapping("/api/v1")
public class ShareLinkController {
    private static final String RISK_HIGH = "high";
    private static final String TARGET_SHARE_LINK = "share_link";

    private final ShareApplicationService service;

    public ShareLinkController(ShareApplicationService service) {
        this.service = service;
    }

    @PostMapping("/mounts/{mountId}/share-links")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_link_create", target = TARGET_SHARE_LINK, riskLevel = RISK_HIGH)
    public ShareApiModels.LinkResponse createLink(@PathVariable UUID mountId,
                                                  @RequestBody ShareApiModels.CreateLinkRequest req,
                                                  Principal p) {
        return ShareApiModels.LinkResponse.from(service.createLink(
                p.getName(), mountId, req.roleId(), req.startAt(), req.expireAt(), req.maxUses(), req.roleStartAt(), req.roleExpireAt()));
    }

    @GetMapping("/share-links")
    public List<ShareApiModels.LinkResponse> listLinks(Principal p) {
        return service.listLinks(p.getName()).stream().map(ShareApiModels.LinkResponse::from).toList();
    }

    @GetMapping("/share-links/{linkId}")
    public ShareApiModels.LinkResponse getLink(@PathVariable UUID linkId, Principal p) {
        return ShareApiModels.LinkResponse.from(service.getLink(p.getName(), linkId));
    }

    @PutMapping("/share-links/{linkId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_link_update", target = TARGET_SHARE_LINK, riskLevel = RISK_HIGH)
    public ShareApiModels.LinkResponse updateLink(@PathVariable UUID linkId,
                                                  @RequestBody ShareApiModels.UpdateLinkRequest req,
                                                  Principal p) {
        return ShareApiModels.LinkResponse.from(service.updateLink(p.getName(), linkId, req.startAt(), req.expireAt(), req.maxUses()));
    }

    @PostMapping("/share-links/{linkId}/revoke")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_link_revoke", target = TARGET_SHARE_LINK, riskLevel = RISK_HIGH)
    public ShareApiModels.LinkResponse revokeLink(@PathVariable UUID linkId, Principal p) {
        return ShareApiModels.LinkResponse.from(service.revokeLink(p.getName(), linkId));
    }

    @DeleteMapping("/share-links/{linkId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "share_link_delete", target = TARGET_SHARE_LINK, riskLevel = RISK_HIGH)
    public ShareApiModels.OperationResponse deleteLink(@PathVariable UUID linkId, Principal p) {
        service.deleteLink(p.getName(), linkId);
        return new ShareApiModels.OperationResponse("delete_share_link", "success");
    }

    @PostMapping("/share-links/resolve")
    public ShareApiModels.ResolveResponse resolve(@RequestBody ShareApiModels.ResolveLinkRequest req, Principal p) {
        return ShareApiModels.ResolveResponse.from(service.resolveLink(p.getName(), req.token()));
    }

    @PostMapping("/shared-mounts/{mountId}/switch-role")
    public ShareApiModels.ResolveResponse switchRole(@PathVariable UUID mountId,
                                                     @RequestBody ShareApiModels.SwitchRoleRequest req,
                                                     Principal p) {
        return ShareApiModels.ResolveResponse.from(service.switchRole(p.getName(), mountId, req.roleId()));
    }
}




