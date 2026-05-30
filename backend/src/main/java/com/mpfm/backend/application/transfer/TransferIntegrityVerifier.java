package com.mpfm.backend.application.transfer;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 传输完整性校验器：统一分片摘要校验与完成态一致性校验，避免控制器与引擎重复实现。
 */
@Component
public class TransferIntegrityVerifier {

    public void verifyChunkSha256(byte[] content, String expectedSha256Hex) {
        if (expectedSha256Hex == null || expectedSha256Hex.isBlank()) {
            return;
        }
        String actual = sha256Hex(content);
        if (!actual.equalsIgnoreCase(expectedSha256Hex.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "chunk checksum mismatch");
        }
    }

    public void verifyUploadComplete(long totalBytes, long chunkSizeBytes, int totalChunks, int completedPartsCount) {
        if (completedPartsCount != totalChunks) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "incomplete upload chunks");
        }
        if (totalBytes < 0 || chunkSizeBytes <= 0 || totalChunks <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid upload session metadata");
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "calculate sha256 failed", ex);
        }
    }
}
