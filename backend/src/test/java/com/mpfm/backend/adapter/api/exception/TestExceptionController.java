package com.mpfm.backend.adapter.api.exception;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置或模型类型，负责承载对应业务语义与约束。
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestExceptionController {

    @GetMapping("/business")
    public String business() {
        throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "bad state");
    }

    @GetMapping("/invalid")
    public String invalid() {
        throw new IllegalArgumentException("bad args");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin-only")
    public String adminOnly() {
        return "ok";
    }
}




