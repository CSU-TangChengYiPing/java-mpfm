package com.mpfm.backend.adapter.api.auth;

import com.mpfm.backend.application.monitor.TransferTelemetryService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户传输速率控制器，提供侧边角标所需的实时速率数据。
 */
@RestController
@RequestMapping("/api/v4/transfers/me")
public class UserTransferController {
    private final TransferTelemetryService transferTelemetryService;

    public UserTransferController(TransferTelemetryService transferTelemetryService) {
        this.transferTelemetryService = transferTelemetryService;
    }

    @GetMapping("/rates")
    public TransferRateResponse rate(Principal principal) {
        TransferTelemetryService.TransferSnapshot snapshot = transferTelemetryService.forCurrentUser(principal.getName());
        return new TransferRateResponse(snapshot.uploadBps(), snapshot.downloadBps());
    }

    /** 当前用户上下传速率响应。 */
    public record TransferRateResponse(long uploadBps, long downloadBps) { }
}

