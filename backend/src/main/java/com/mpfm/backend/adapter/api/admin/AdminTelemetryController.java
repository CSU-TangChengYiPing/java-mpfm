package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.SystemTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import java.util.List;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端遥测控制器，提供用户实时传输统计视图。
 */
@RestController
@RequestMapping("/api/v1/admin/telemetry")
@PreAuthorize("hasRole('ROOT')")
public class AdminTelemetryController {
    private static final String ACTION_PAUSE_UPLOAD = "pause_upload";
    private static final String ACTION_RESUME_UPLOAD = "resume_upload";
    private static final String ACTION_PAUSE_DOWNLOAD = "pause_download";
    private static final String ACTION_RESUME_DOWNLOAD = "resume_download";
    private static final String ACTION_KICK_UPLOAD = "kick_upload";
    private static final String ACTION_KICK_DOWNLOAD = "kick_download";
    private static final String ACTION_KICK_ALL = "kick_all";
    private static final String ACTION_BIND_QOS = "bind_qos";

    private final TransferTelemetryService transferTelemetryService;
    private final SystemTelemetryService systemTelemetryService;
    private final UserTransferGovernanceService userTransferGovernanceService;

    public AdminTelemetryController(TransferTelemetryService transferTelemetryService,
                                    SystemTelemetryService systemTelemetryService,
                                    UserTransferGovernanceService userTransferGovernanceService) {
        this.transferTelemetryService = transferTelemetryService;
        this.systemTelemetryService = systemTelemetryService;
        this.userTransferGovernanceService = userTransferGovernanceService;
    }

    @GetMapping("/users/transfer")
    public List<UserTransferResponse> listUserTransferStats() {
        return transferTelemetryService.forAllUsers().stream().map(UserTransferResponse::from).toList();
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(name = "minutes", defaultValue = "3") int minutes) {
        int safeMinutes = Math.max(1, Math.min(60 * 24, minutes));
        return new DashboardResponse(
                currentSystemOverview(),
                systemHistory(safeMinutes),
                listUserTransferStats());
    }

    @GetMapping("/users/{username}/history")
    public List<TransferTelemetryService.TransferTimelinePoint> userTransferHistory(@PathVariable String username,
                                                                                    @RequestParam(name = "minutes", defaultValue = "5") int minutes) {
        return transferTelemetryService.userTimeline(username, Math.max(1, Math.min(15, minutes)));
    }

    @PostMapping("/users/{username}/governance")
    public GovernanceResponse governUserTransfer(@PathVariable String username,
                                                 @org.springframework.web.bind.annotation.RequestBody GovernanceRequest request,
                                                 Principal principal) {
        String action = request.action() == null ? "" : request.action().trim().toLowerCase(java.util.Locale.ROOT);
        int kickedCount = 0;
        if (ACTION_PAUSE_UPLOAD.equals(action)) {
            userTransferGovernanceService.pauseUpload(username, true, principal.getName());
        } else if (ACTION_RESUME_UPLOAD.equals(action)) {
            userTransferGovernanceService.pauseUpload(username, false, principal.getName());
        } else if (ACTION_PAUSE_DOWNLOAD.equals(action)) {
            userTransferGovernanceService.pauseDownload(username, true, principal.getName());
        } else if (ACTION_RESUME_DOWNLOAD.equals(action)) {
            userTransferGovernanceService.pauseDownload(username, false, principal.getName());
        } else if (ACTION_KICK_UPLOAD.equals(action)) {
            kickedCount = userTransferGovernanceService.kickActiveTasks(username, "upload");
        } else if (ACTION_KICK_DOWNLOAD.equals(action)) {
            kickedCount = userTransferGovernanceService.kickActiveTasks(username, "download");
        } else if (ACTION_KICK_ALL.equals(action)) {
            kickedCount = userTransferGovernanceService.kickActiveTasks(username, "all");
        } else if (ACTION_BIND_QOS.equals(action)) {
            userTransferGovernanceService.bindQosLimit(username, request.policyId(), principal.getName());
        } else {
            throw new com.mpfm.backend.common.error.BusinessException(com.mpfm.backend.common.error.ErrorCode.VALIDATION_ERROR, "unsupported governance action");
        }
        UserTransferGovernanceService.GovernanceState state = userTransferGovernanceService.stateOf(username);
        return new GovernanceResponse(state.username(), state.uploadPaused(), state.downloadPaused(), kickedCount, action, "success");
    }

    @GetMapping("/users/{username}/governance")
    public GovernanceResponse userGovernanceState(@PathVariable String username) {
        UserTransferGovernanceService.GovernanceState state = userTransferGovernanceService.stateOf(username);
        return new GovernanceResponse(state.username(), state.uploadPaused(), state.downloadPaused(), 0, "query", "success");
    }

    @GetMapping("/system/overview")
    public SystemTelemetryService.SystemSnapshot currentSystemOverview() {
        return systemTelemetryService.current();
    }

    @GetMapping("/system/history")
    public List<SystemTelemetryService.SystemSnapshot> systemHistory(@RequestParam(name = "minutes", defaultValue = "15") int minutes) {
        return systemTelemetryService.history(Math.max(1, Math.min(60 * 24, minutes)));
    }

    /** 管理端用户实时传输快照响应。 */
    public record UserTransferResponse(String username,
                                       long uploadBps,
                                       long downloadBps,
                                       int activeUploadTasks,
                                       int activeDownloadTasks,
                                       int activeChunks,
                                       long totalBps) {
        static UserTransferResponse from(TransferTelemetryService.TransferSnapshot snapshot) {
            return new UserTransferResponse(
                    snapshot.username(),
                    snapshot.uploadBps(),
                    snapshot.downloadBps(),
                    snapshot.activeUploadTasks(),
                    snapshot.activeDownloadTasks(),
                    snapshot.activeChunks(),
                    snapshot.totalBps());
        }
    }

    /** 用户传输治理请求。 */
    public record GovernanceRequest(String action, String policyId) { }

    /** 用户传输治理响应。 */
    public record GovernanceResponse(String username, boolean uploadPaused, boolean downloadPaused,
                                     int kickedTasks, String action, String status) { }

    /** 监控页聚合响应：首屏所需数据一次返回，减少前端多请求拼装。 */
    public record DashboardResponse(SystemTelemetryService.SystemSnapshot overview,
                                    List<SystemTelemetryService.SystemSnapshot> history,
                                    List<UserTransferResponse> users) { }
}
