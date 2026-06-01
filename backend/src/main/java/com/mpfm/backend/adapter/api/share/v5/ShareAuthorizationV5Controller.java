package com.mpfm.backend.adapter.api.share.v5;

import com.mpfm.backend.adapter.api.file.FileApiModels;
import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v5 授权链接控制器，提供链接发放、解析、撤销与并集权限查询能力。
 */
@RestController
@RequestMapping("/api/v5")
public class ShareAuthorizationV5Controller {
    private static final String IF_MATCH = "If-Match";

    private final ShareAuthorizationV5Service service;
    private final FileApplicationService fileApplicationService;

    public ShareAuthorizationV5Controller(ShareAuthorizationV5Service service,
                                          FileApplicationService fileApplicationService) {
        this.service = service;
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping("/mounts/{mountId}/share-links")
    public ShareAuthorizationV5ApiModels.LinkResponse createLink(@PathVariable UUID mountId,
                                                                 @RequestBody ShareAuthorizationV5ApiModels.CreateLinkRequest req,
                                                                 @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                 Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.LinkResponse.from(service.createLink(
                principal.getName(), mountId, req.roleId(), req.startAt(), req.expireAt(), req.maxUses(), req.roleStartAt(), req.roleExpireAt()));
    }

    @PostMapping("/share-links/resolve")
    public ShareAuthorizationV5ApiModels.ResolveResponse resolve(@RequestBody ShareAuthorizationV5ApiModels.ResolveLinkRequest req,
                                                                 @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                 Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.ResolveResponse.from(service.resolveLink(principal.getName(), req.token()));
    }

    @PostMapping("/share-links/{linkId}/revoke")
    public ShareAuthorizationV5ApiModels.LinkResponse revoke(@PathVariable UUID linkId,
                                                             @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                             Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.LinkResponse.from(service.revokeLink(principal.getName(), linkId));
    }

    @DeleteMapping("/share-links/{linkId}")
    public void delete(@PathVariable UUID linkId,
                       @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                       Principal principal) {
        requireIfMatch(ifMatch);
        service.deleteLink(principal.getName(), linkId);
    }

    @GetMapping("/mounts/{mountId}/share-links")
    public List<ShareAuthorizationV5ApiModels.LinkResponse> listLinks(@PathVariable UUID mountId, Principal principal) {
        return service.listLinks(principal.getName(), mountId).stream().map(ShareAuthorizationV5ApiModels.LinkResponse::from).toList();
    }

    @GetMapping("/mounts/{mountId}/my-roles")
    public List<ShareAuthorizationV5ApiModels.MyRoleResponse> myRoles(@PathVariable UUID mountId, Principal principal) {
        return service.listMyRoles(principal.getName(), mountId).stream().map(ShareAuthorizationV5ApiModels.MyRoleResponse::from).toList();
    }

    @GetMapping("/mounts/{mountId}/granted-roles")
    public List<ShareAuthorizationV5ApiModels.GrantedRoleResponse> grantedRoles(@PathVariable UUID mountId, Principal principal) {
        return service.listGrantedRoles(principal.getName(), mountId).stream().map(ShareAuthorizationV5ApiModels.GrantedRoleResponse::from).toList();
    }

    @PutMapping("/mounts/{mountId}/granted-roles/{granteeUserId}/roles/{roleId}")
    public ShareAuthorizationV5ApiModels.GrantedRoleResponse updateGrantedRole(@PathVariable UUID mountId,
                                                                                @PathVariable UUID granteeUserId,
                                                                                @PathVariable UUID roleId,
                                                                                @RequestBody ShareAuthorizationV5ApiModels.UpdateGrantedRoleRequest req,
                                                                                @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                Principal principal) {
        requireIfMatch(ifMatch);
        UUID nextRoleId;
        try {
            nextRoleId = UUID.fromString(req.roleId());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid roleId");
        }
        return ShareAuthorizationV5ApiModels.GrantedRoleResponse.from(
                service.updateGrantedRole(principal.getName(), mountId, granteeUserId, roleId, nextRoleId, req.roleExpireAt()));
    }

    @DeleteMapping("/mounts/{mountId}/granted-roles/{granteeUserId}/roles/{roleId}")
    public void revokeGrantedRole(@PathVariable UUID mountId,
                                  @PathVariable UUID granteeUserId,
                                  @PathVariable UUID roleId,
                                  @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                  Principal principal) {
        requireIfMatch(ifMatch);
        service.revokeGrantedRole(principal.getName(), mountId, granteeUserId, roleId);
    }

    @PostMapping("/mounts/{mountId}/role-templates")
    public ShareAuthorizationV5ApiModels.RoleTemplateResponse createRoleTemplate(@PathVariable UUID mountId,
                                                                                  @RequestBody ShareAuthorizationV5ApiModels.CreateRoleTemplateRequest req,
                                                                                  @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                  Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.RoleTemplateResponse.from(service.createRoleTemplate(
                principal.getName(), mountId, req.name(), req.defaultVisible(), req.defaultRead(), req.defaultWrite()));
    }

    @PutMapping("/role-templates/{templateId}")
    public ShareAuthorizationV5ApiModels.RoleTemplateResponse updateRoleTemplate(@PathVariable UUID templateId,
                                                                                  @RequestBody ShareAuthorizationV5ApiModels.UpdateRoleTemplateRequest req,
                                                                                  @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                  Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.RoleTemplateResponse.from(service.updateRoleTemplate(
                principal.getName(), templateId, req.name(), req.defaultVisible(), req.defaultRead(), req.defaultWrite()));
    }

    @DeleteMapping("/role-templates/{templateId}")
    public ShareAuthorizationV5ApiModels.RoleTemplateResponse deleteRoleTemplate(@PathVariable UUID templateId,
                                                                                  @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                  Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.RoleTemplateResponse.from(service.deleteRoleTemplate(principal.getName(), templateId));
    }

    @PutMapping("/role-templates/{templateId}/privileges")
    public ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse upsertTemplatePrivilege(@PathVariable UUID templateId,
                                                                                                @RequestBody ShareAuthorizationV5ApiModels.UpsertRoleTemplatePrivilegeRequest req,
                                                                                                @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                                Principal principal) {
        requireIfMatch(ifMatch);
        return ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse.from(service.upsertRoleTemplatePrivilege(
                principal.getName(), templateId, req.targetPath(), req.allowVisible(), req.allowRead(), req.allowWrite()));
    }

    @PutMapping("/role-templates/{templateId}/privileges/batch")
    public List<ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse> upsertTemplatePrivilegesBatch(@PathVariable UUID templateId,
                                                                                                            @RequestBody ShareAuthorizationV5ApiModels.UpsertRoleTemplatePrivilegeBatchRequest req,
                                                                                                            @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                                                                            Principal principal) {
        requireIfMatch(ifMatch);
        return service.upsertRoleTemplatePrivilegesBatch(
                        principal.getName(), templateId, req.targetPaths(), req.allowVisible(), req.allowRead(), req.allowWrite())
                .stream()
                .map(ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse::from)
                .toList();
    }

    @GetMapping("/mounts/{mountId}/role-templates")
    public List<ShareAuthorizationV5ApiModels.RoleTemplateResponse> listRoleTemplates(@PathVariable UUID mountId, Principal principal) {
        return service.listRoleTemplates(principal.getName(), mountId).stream().map(ShareAuthorizationV5ApiModels.RoleTemplateResponse::from).toList();
    }

    @GetMapping("/role-templates/{templateId}/privileges")
    public List<ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse> listTemplatePrivileges(@PathVariable UUID templateId,
                                                                                                      Principal principal) {
        return service.listRoleTemplatePrivileges(principal.getName(), templateId).stream()
                .map(ShareAuthorizationV5ApiModels.RoleTemplatePrivilegeResponse::from).toList();
    }

    @DeleteMapping("/role-templates/{templateId}/privileges/{privilegeId}")
    public void deleteTemplatePrivilege(@PathVariable UUID templateId,
                                        @PathVariable UUID privilegeId,
                                        @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                        Principal principal) {
        requireIfMatch(ifMatch);
        service.deleteRoleTemplatePrivilege(principal.getName(), templateId, privilegeId);
    }

    @PostMapping("/mounts/{mountId}/permissions/template-preview-batch")
    public Map<String, ShareAuthorizationV5ApiModels.TemplateEffectiveBatchResponse> templatePreviewBatch(@PathVariable UUID mountId,
                                                                                                            @RequestBody ShareAuthorizationV5ApiModels.TemplateEffectiveBatchRequest req,
                                                                                                            Principal principal) {
        UUID templateId;
        try {
            templateId = UUID.fromString(req.templateId());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid templateId");
        }
        Map<String, ShareAuthorizationV5Service.EffectivePermissionResult> result =
                service.effectiveByTemplateBatch(principal.getName(), mountId, templateId, req.paths());
        Map<String, ShareAuthorizationV5ApiModels.TemplateEffectiveBatchResponse> out = new LinkedHashMap<>();
        result.forEach((path, value) -> out.put(path, ShareAuthorizationV5ApiModels.TemplateEffectiveBatchResponse.from(value)));
        return out;
    }

    @GetMapping("/mounts/{mountId}/permissions/template-files")
    public FileApiModels.FileItemsResponse templateFiles(@PathVariable UUID mountId,
                                                         @RequestParam String templateId,
                                                         @RequestParam String virtualPath,
                                                         Principal principal) {
        UUID parsedTemplateId;
        try {
            parsedTemplateId = UUID.fromString(templateId);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid templateId");
        }
        List<FileApplicationService.EntryResult> entries = fileApplicationService.list(principal.getName(), virtualPath);
        List<String> templatePaths = new ArrayList<>();
        for (FileApplicationService.EntryResult entry : entries) {
            templatePaths.add(toTemplateRelativePath(mountId, entry.path()));
        }
        Map<String, ShareAuthorizationV5Service.EffectivePermissionResult> permissionMap =
                service.effectiveByTemplateBatch(principal.getName(), mountId, parsedTemplateId, templatePaths);
        List<FileApiModels.FileEntryResponse> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += 1) {
            FileApplicationService.EntryResult entry = entries.get(i);
            String templatePath = templatePaths.get(i);
            ShareAuthorizationV5Service.EffectivePermissionResult perm = permissionMap.get(templatePath);
            boolean visible = perm != null && perm.canVisible();
            boolean readable = perm != null && perm.canRead();
            boolean writable = perm != null && perm.canWrite();
            items.add(new FileApiModels.FileEntryResponse(
                    entry.path(), entry.name(), entry.type(), entry.sizeBytes(), entry.mtime(),
                    entry.linkCount(), visible, readable, writable, entry.etag(), entry.version()
            ));
        }
        return new FileApiModels.FileItemsResponse(items, new FileApiModels.PageMeta(1, items.size(), items.size()));
    }

    @GetMapping("/share-links/my-roles")
    public List<ShareAuthorizationV5ApiModels.MyRoleSummaryResponse> myRolesSummary(Principal principal) {
        return service.listMyRolesSummary(principal.getName()).stream().map(ShareAuthorizationV5ApiModels.MyRoleSummaryResponse::from).toList();
    }

    @GetMapping("/permissions/effective")
    public ShareAuthorizationV5ApiModels.PermissionResponse effective(@RequestParam UUID mountId,
                                                                      @RequestParam String path,
                                                                      Principal principal) {
        return ShareAuthorizationV5ApiModels.PermissionResponse.from(service.effective(principal.getName(), mountId, path));
    }

    private void requireIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match header is required");
        }
    }

    /** 归一化目标模板路径：从 virtualPath 提取挂载内相对路径（/ 开头）。 */
    private String toTemplateRelativePath(UUID mountId, String virtualPath) {
        String normalized = (virtualPath == null ? "/" : virtualPath.trim()).replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.contains("/./")) {
            normalized = normalized.replace("/./", "/");
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        String[] parts = normalized.split("/");
        int scopeIndex = -1;
        for (int i = 1; i < parts.length; i += 1) {
            if ("personal".equals(parts[i]) || "shared".equals(parts[i])) {
                scopeIndex = i;
                break;
            }
        }
        if (scopeIndex >= 0) {
            StringBuilder relative = new StringBuilder("/");
            for (int i = scopeIndex + 2; i < parts.length; i += 1) {
                if (parts[i] == null || parts[i].isBlank() || ".".equals(parts[i])) {
                    continue;
                }
                if (relative.length() > 1) {
                    relative.append("/");
                }
                relative.append(parts[i]);
            }
            return relative.toString();
        }
        String token = mountId.toString();
        String prefixA = "/personal/" + token;
        String prefixB = "/shared/" + token;
        if (normalized.equals(prefixA) || normalized.equals(prefixB)) {
            return "/";
        }
        if (normalized.startsWith(prefixA + "/")) {
            return normalized.substring(prefixA.length());
        }
        if (normalized.startsWith(prefixB + "/")) {
            return normalized.substring(prefixB.length());
        }
        return normalized;
    }
}
