package com.mpfm.backend.adapter.api;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康控制器，提供服务存活探针接口。
 */
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {
    private final Path devCertPath;

    public HealthController(@Value("${mpfm.security.dev-cert-path:./certs/mpfm-local.cer}") String devCertPath) {
        this.devCertPath = Path.of(devCertPath).toAbsolutePath().normalize();
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ok");
    }

    /**
     * 开发证书下载入口：允许匿名访问，用于首次接入时导入本地自签名证书后建立 HTTPS/WebDAV 连接。
     */
    @GetMapping("/dev-cert")
    public ResponseEntity<byte[]> downloadDevCert() {
        if (!Files.exists(devCertPath) || !Files.isRegularFile(devCertPath)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "dev certificate not found");
        }
        try {
            byte[] certBytes = Files.readAllBytes(devCertPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("mpfm-local.cer").build().toString())
                    .contentType(MediaType.parseMediaType("application/pkix-cert"))
                    .body(certBytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read dev certificate failed", ex);
        }
    }

    /** 存活探针响应，`status=ok` 表示服务可用。 */
    public record PingResponse(String status) {
    }
}




