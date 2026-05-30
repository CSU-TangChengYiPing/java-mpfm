package com.mpfm.backend.application.transfer;

import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 上传分片引擎：负责分片写入、幂等重传与完成态校验，不处理鉴权与任务编排。
 */
@Component
public class UploadChunkEngine {
    private static final int SINGLE_CHUNK_COUNT = 1;
    private final TransferBandwidthLimiter bandwidthLimiter;
    private final TransferIntegrityVerifier integrityVerifier;

    public UploadChunkEngine(TransferBandwidthLimiter bandwidthLimiter, TransferIntegrityVerifier integrityVerifier) {
        this.bandwidthLimiter = bandwidthLimiter;
        this.integrityVerifier = integrityVerifier;
    }

    // 写入上传分片
    public TransferChunkService.UploadSession writePart(String username,
                                                        TransferChunkService.UploadSession session,
                                                        int partNumber,
                                                        byte[] content,
                                                        String chunkSha256Hex) {
        validatePartNumber(partNumber, session.totalChunks());
        long expectedSize = expectedChunkSize(session.totalBytes(), session.chunkSizeBytes(), session.totalChunks(), partNumber);
        if (content.length != expectedSize) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "chunk size mismatch");
        }
        integrityVerifier.verifyChunkSha256(content, chunkSha256Hex);

        Set<Integer> done = new HashSet<>(session.completedParts());
        if (done.contains(partNumber)) {
            return session.withUpdatedAt(Instant.now().toString());
        }

        bandwidthLimiter.checkUpload(username, content.length);
        Path dataFile = Path.of(session.dataFilePath());
        try {
            Files.createDirectories(dataFile.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "rw")) {
                long offset = (long) (partNumber - 1) * session.chunkSizeBytes();
                raf.seek(offset);
                raf.write(content);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "write chunk failed", ex);
        }
        done.add(partNumber);
        return session.withCompletedParts(new ArrayList<>(done), Instant.now().toString());
    }

    // 校验上传完成
    public TransferChunkService.UploadSession complete(TransferChunkService.UploadSession session) {
        integrityVerifier.verifyUploadComplete(
                session.totalBytes(),
                session.chunkSizeBytes(),
                session.totalChunks(),
                session.completedParts().size()
        );
        return session.withStatus("SUCCESS", Instant.now().toString());
    }

    // 校验分片序号
    private void validatePartNumber(int partNumber, int totalChunks) {
        if (partNumber <= 0 || partNumber > totalChunks) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid chunk part number");
        }
    }

    // 校验分片大小
    private long expectedChunkSize(long totalBytes, long chunkSize, int totalChunks, int partNumber) {
        if (totalChunks <= SINGLE_CHUNK_COUNT) {
            return totalBytes;
        }
        if (partNumber < totalChunks) {
            return chunkSize;
        }
        long remain = totalBytes - (long) (totalChunks - 1) * chunkSize;
        return Math.max(0L, remain);
    }
}
