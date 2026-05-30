package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.infrastructure.persistence.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端审计查询控制器，提供按时间窗口检索审计事件的只读接口。
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT')")
    public List<AuditEventResponse> events(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        OffsetDateTime fromTime = from == null || from.isBlank() ? OffsetDateTime.now().minusDays(30) : OffsetDateTime.parse(from);
        OffsetDateTime toTime = to == null || to.isBlank() ? OffsetDateTime.now() : OffsetDateTime.parse(to);
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromTime, toTime).stream()
                .map(item -> new AuditEventResponse(
                        item.getId().toString(),
                        item.getOperator(),
                        item.getAction(),
                        item.getTarget(),
                        item.getResult(),
                        item.getErrorCode(),
                        item.getCreatedAt().toString()
                ))
                .toList();
    }

    /** 审计事件响应模型，返回操作人、动作、结果与发生时间等审计字段。 */
    public record AuditEventResponse(String eventId, String operator, String action, String target,
                                     String result, String errorCode, String createdAt) {
    }
}




