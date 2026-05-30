package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.security.QosPolicyService;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 QoS 控制器（极简版）：仅保留策略名称/上下行速率与用户自定义限速。
 */
@RestController
@RequestMapping("/api/v1/admin/qos")
@PreAuthorize("hasRole('ROOT')")
public class AdminQosController {
    private final QosPolicyService qosPolicyService;

    public AdminQosController(QosPolicyService qosPolicyService) {
        this.qosPolicyService = qosPolicyService;
    }

    @GetMapping("/policies")
    public List<QosPolicyService.QosPolicy> policies() {
        return qosPolicyService.listPolicies();
    }

    @PostMapping("/policies")
    public QosPolicyService.QosPolicy createPolicy(@RequestBody CreatePolicyRequest request, Principal principal) {
        return qosPolicyService.createPolicy(
                new QosPolicyService.CreatePolicyCommand(
                        request.name(),
                        request.maxUploadBps(),
                        request.maxDownloadBps(),
                        principal.getName()));
    }

    @PutMapping("/policies/{policyId}")
    public QosPolicyService.QosPolicy updatePolicy(@PathVariable String policyId,
                                                   @RequestBody UpdatePolicyRequest request,
                                                   Principal principal) {
        return qosPolicyService.updatePolicy(
                policyId,
                new QosPolicyService.UpdatePolicyCommand(
                        request.name(),
                        request.maxUploadBps(),
                        request.maxDownloadBps(),
                        principal.getName()));
    }

    @DeleteMapping("/policies/{policyId}")
    public BatchDeleteResponse deletePolicy(@PathVariable String policyId) {
        int removed = qosPolicyService.batchDeletePolicies(List.of(policyId));
        return new BatchDeleteResponse(removed);
    }

    @PostMapping("/policies/batch-delete")
    public BatchDeleteResponse batchDelete(@RequestBody BatchDeleteRequest request) {
        return new BatchDeleteResponse(qosPolicyService.batchDeletePolicies(request.policyIds()));
    }

    @PostMapping("/users/{username}/bind")
    public BindResponse bind(@PathVariable String username, @RequestBody BindPolicyRequest request, Principal principal) {
        qosPolicyService.bindUserPolicy(username, request.policyId(), principal.getName());
        return new BindResponse(username, request.policyId(), "success");
    }

    @GetMapping("/users/{username}/custom-limit")
    public QosPolicyService.UserCustomLimit userCustomLimit(@PathVariable String username) {
        return qosPolicyService.getUserCustomLimit(username);
    }

    @PutMapping("/users/{username}/custom-limit")
    public QosPolicyService.UserCustomLimit updateUserCustomLimit(@PathVariable String username,
                                                                  @RequestBody UpdateUserCustomLimitRequest request,
                                                                  Principal principal) {
        return qosPolicyService.updateUserCustomLimit(
                username,
                request.maxUploadBps(),
                request.maxDownloadBps(),
                principal.getName());
    }

    public record CreatePolicyRequest(String name, long maxUploadBps, long maxDownloadBps) { }

    public record UpdatePolicyRequest(String name, long maxUploadBps, long maxDownloadBps) { }

    public record BatchDeleteRequest(List<String> policyIds) { }

    public record BatchDeleteResponse(int removedCount) { }

    public record BindPolicyRequest(@NotBlank String policyId) { }

    public record BindResponse(String username, String policyId, String status) { }

    public record UpdateUserCustomLimitRequest(long maxUploadBps, long maxDownloadBps) { }
}

