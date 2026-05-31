package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.transfer.TransferChunkService;
import com.mpfm.backend.application.transfer.TransferDirectUploadService;
import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverPutRequest;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.Principal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** v4 分片传输控制器：提供上传会话与下载分片读取接口。 */
@RestController
@RequestMapping("/api/v4/transfers")
public class TransferChunkV4Controller {
    private static final String DIRECT_TOKEN_HEADER = "X-Direct-Token";
    private static final String RUNTIME_PATH_HEADER = "X-Upload-Virtual-Path";
    private static final String RUNTIME_FILENAME_HEADER = "X-Upload-Filename";
    private static final String RUNTIME_SIZE_HEADER = "X-Upload-Size";
    private static final String RANGE_HEADER = "Range";
    private static final String IF_RANGE_HEADER = "If-Range";
    private final TransferChunkService transferChunkService;
    private final TransferDirectUploadService transferDirectUploadService;
    private final FileApplicationService fileApplicationService;
    private final DriverFactory driverFactory;
    private final NamespaceResolver namespaceResolver;
    private final DownloadResponseSupport downloadResponseSupport;
    private final UserTransferGovernanceService userTransferGovernanceService;
    private final TransferTelemetryService transferTelemetryService;
    private final TransferBandwidthLimiter transferBandwidthLimiter;

    public TransferChunkV4Controller(TransferChunkService transferChunkService,
                                     TransferDirectUploadService transferDirectUploadService,
                                     FileApplicationService fileApplicationService,
                                     DriverFactory driverFactory,
                                     NamespaceResolver namespaceResolver,
                                     DownloadResponseSupport downloadResponseSupport,
                                     UserTransferGovernanceService userTransferGovernanceService,
                                     TransferTelemetryService transferTelemetryService,
                                     TransferBandwidthLimiter transferBandwidthLimiter) {
        this.transferChunkService = transferChunkService;
        this.transferDirectUploadService = transferDirectUploadService;
        this.fileApplicationService = fileApplicationService;
        this.driverFactory = driverFactory;
        this.namespaceResolver = namespaceResolver;
        this.downloadResponseSupport = downloadResponseSupport;
        this.userTransferGovernanceService = userTransferGovernanceService;
        this.transferTelemetryService = transferTelemetryService;
        this.transferBandwidthLimiter = transferBandwidthLimiter;
    }

    @PostMapping(value = "/uploads/runtime/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadSessionResponse createRuntimeUploadTask(@RequestParam String virtualPath,
                                                         @RequestParam("file") MultipartFile file,
                                                         Principal principal) throws Exception {
        if (file == null || principal == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid request");
        }
        String decodedVirtualPath = virtualPath == null ? null : URLDecoder.decode(virtualPath, StandardCharsets.UTF_8);
        String normalizedVirtualPath = (decodedVirtualPath == null || decodedVirtualPath.isBlank()) ? "." : decodedVirtualPath;
        String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload.bin" : file.getOriginalFilename();
        return UploadSessionResponse.from(
                transferChunkService.createStreamUpload(
                        principal.getName(),
                        normalizedVirtualPath,
                        filename,
                        file.getSize(),
                        file.getInputStream()));
    }

    @PostMapping(value = "/uploads/runtime/tasks", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadSessionResponse createRuntimeUploadTaskByStream(@RequestHeader(value = RUNTIME_PATH_HEADER, required = false) String virtualPath,
                                                                 @RequestHeader(value = RUNTIME_FILENAME_HEADER, required = false) String filename,
                                                                 @RequestHeader(value = RUNTIME_SIZE_HEADER, required = false) Long totalBytesHeader,
                                                                 HttpServletRequest request,
                                                                 Principal principal) throws Exception {
        if (principal == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid request");
        }
        String decodedVirtualPath = virtualPath == null ? null : URLDecoder.decode(virtualPath, StandardCharsets.UTF_8);
        String normalizedVirtualPath = (decodedVirtualPath == null || decodedVirtualPath.isBlank()) ? "." : decodedVirtualPath;
        String decodedFilename = filename == null ? null : URLDecoder.decode(filename, StandardCharsets.UTF_8);
        String normalizedFilename = (decodedFilename == null || decodedFilename.isBlank()) ? "upload.bin" : decodedFilename;
        long requestContentLength = request.getContentLengthLong();
        long totalBytes = totalBytesHeader != null && totalBytesHeader >= 0 ? totalBytesHeader : requestContentLength;
        if (totalBytes < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "missing upload size");
        }
        NamespaceResolver.ResolveResult resolved =
                namespaceResolver.resolve(principal.getName(), normalizedVirtualPath, true, true);
        if (!"local".equalsIgnoreCase(resolved.mount().getType())) {
            if ("sftp".equalsIgnoreCase(resolved.mount().getType())) {
                writeSftpStream(principal.getName(), resolved, normalizedFilename, request.getInputStream(), totalBytes);
                return new UploadSessionResponse(
                        UUID.randomUUID().toString(),
                        "",
                        "SUCCESS",
                        totalBytes,
                        Math.max(1L, totalBytes),
                        1,
                        List.of(1),
                        Instant.now().toString(),
                        "stream",
                        ""
                );
            }
            String dirPath = ".".equals(resolved.relPath()) ? "." : resolved.relPath();
            byte[] payload = request.getInputStream().readAllBytes();
            if (payload.length != totalBytes) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "stream upload size mismatch");
            }
            driverFactory.resolve(resolved.mount().getType()).put(
                    new DriverContext(principal.getName(), resolved.mount()),
                    new DriverPutRequest(dirPath, normalizedFilename, payload, true)
            );
            return new UploadSessionResponse(
                    UUID.randomUUID().toString(),
                    "",
                    "SUCCESS",
                    payload.length,
                    Math.max(1L, payload.length),
                    1,
                    List.of(1),
                    Instant.now().toString(),
                    "stream",
                    ""
            );
        }
        return UploadSessionResponse.from(
                transferChunkService.createStreamUpload(
                        principal.getName(),
                        normalizedVirtualPath,
                        normalizedFilename,
                        totalBytes,
                        request.getInputStream()));
    }

    @GetMapping("/uploads/capabilities")
    public UploadCapabilityResponse capabilities(@org.springframework.web.bind.annotation.RequestParam String virtualPath,
                                                 Principal principal) {
        return UploadCapabilityResponse.from(transferDirectUploadService.getUploadCapability(principal.getName(), virtualPath));
    }

    @PostMapping("/uploads/direct/tickets")
    public DirectTicketResponse createDirectTicket(@RequestBody DirectTicketRequest request, Principal principal) {
        return DirectTicketResponse.from(
                transferDirectUploadService.createDirectUploadTicket(
                        principal.getName(),
                        request.virtualPath(),
                        request.filename(),
                        request.totalBytes(),
                        request.chunkSizeBytes()
                )
        );
    }

    @PostMapping("/uploads/direct/{taskId}/acks")
    public UploadSessionResponse ackDirect(@PathVariable String taskId,
                                           @RequestBody DirectAckRequest request,
                                           Principal principal) {
        return UploadSessionResponse.from(
                transferDirectUploadService.ackDirectUpload(
                        principal.getName(),
                        taskId,
                        new TransferDirectUploadService.DirectUploadAckRequest(
                                request.partNumber(),
                                request.completed(),
                                request.failed(),
                                request.errorCode()
                        )
                )
        );
    }

    @org.springframework.web.bind.annotation.PutMapping(
            value = "/uploads/direct/{taskId}/parts/{partNumber}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadSessionResponse uploadDirectPart(@PathVariable String taskId,
                                                  @PathVariable int partNumber,
                                                  @RequestHeader(value = DIRECT_TOKEN_HEADER, required = false) String directToken,
                                                  HttpServletRequest request,
                                                  Principal principal) {
        long expectedBytes = request.getContentLengthLong();
        return UploadSessionResponse.from(
                transferDirectUploadService.uploadDirectPart(
                        principal.getName(),
                        taskId,
                        partNumber,
                        directToken,
                        expectedBytes,
                        requestInputStream(request))
        );
    }

    private InputStream requestInputStream(HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read request stream failed", ex);
        }
    }

    @GetMapping("/uploads/{uploadId}")
    public UploadSessionResponse getUpload(@PathVariable String uploadId, Principal principal) {
        return UploadSessionResponse.from(transferChunkService.getUploadSession(principal.getName(), uploadId));
    }

    /** v4 直链下载入口：默认跳转到代理下载 */
    @GetMapping("/downloads/direct")
    public ResponseEntity<Void> downloadDirect(@RequestParam String virtualPath) {
        String encoded = URLEncoder.encode(virtualPath, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/api/v4/transfers/downloads/proxy?virtualPath=" + encoded)
                .build();
    }

    /** v4 代理下载入口：支持 Range/If-Range，返回 200/206。 */
    @GetMapping("/downloads/proxy")
    public ResponseEntity<StreamingResponseBody> downloadProxy(@RequestParam String virtualPath,
                                                               HttpServletRequest request,
                                                               Principal principal) {
        userTransferGovernanceService.ensureDownloadAllowed(principal.getName());
        downloadResponseSupport.validateRange(request.getHeader(RANGE_HEADER));
        FileApiModels.FileEntryResponse stat = FileApiModels.FileEntryResponse.from(
                fileApplicationService.stat(principal.getName(), virtualPath));
        NamespaceResolver.ResolveResult resolved = namespaceResolver.resolve(principal.getName(), virtualPath, false, true);
        return downloadResponseSupport.download(
                "/api/v4/transfers/downloads/proxy",
                principal.getName(),
                resolved,
                stat.etag(),
                stat.mtime(),
                request.getHeader(RANGE_HEADER),
                request.getHeader(IF_RANGE_HEADER));
    }

    private void writeSftpStream(String username,
                                 NamespaceResolver.ResolveResult resolved,
                                 String filename,
                                 InputStream inputStream,
                                 long expectedBytes) throws IOException {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(new DriverContext(username, resolved.mount()));
        String dirPath = toSftpTargetPath(connection.basePath(), resolved.relPath());
        String targetPath = SftpDriverUtil.join(dirPath, filename);
        long written = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (SftpClient.CloseableHandle handle = connection.sftpClient().open(
                targetPath,
                SftpClient.OpenMode.Create,
                SftpClient.OpenMode.Write,
                SftpClient.OpenMode.Truncate)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                connection.sftpClient().write(handle, written, buffer, 0, read);
                written += read;
            }
            if (written != expectedBytes) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "stream upload size mismatch");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp stream upload failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    private String toSftpTargetPath(String basePath, String relPath) {
        String normalizedRel = SftpDriverUtil.normalizePath(relPath);
        if (basePath == null || ".".equals(basePath)) {
            return ".".equals(normalizedRel) ? "/" : (normalizedRel.startsWith("/") ? normalizedRel : "/" + normalizedRel);
        }
        if (".".equals(normalizedRel)) {
            return basePath;
        }
        return SftpDriverUtil.join(basePath, normalizedRel);
    }

    /** 直传票据请求。 */
    public record DirectTicketRequest(String virtualPath, String filename, long totalBytes, Long chunkSizeBytes) { }
    /** 直传回执请求。 */
    public record DirectAckRequest(int partNumber, boolean completed, boolean failed, String errorCode) { }

    /** 上传会话响应。 */
    public record UploadSessionResponse(String uploadId, String taskId, String status, long totalBytes,
                                        long chunkSizeBytes, int totalChunks, java.util.List<Integer> completedParts,
                                        String updatedAt, String uploadMode, String providerSessionId) {
        static UploadSessionResponse from(TransferChunkService.UploadSession session) {
            return new UploadSessionResponse(
                    session.uploadId(),
                    session.taskId(),
                    session.status(),
                    session.totalBytes(),
                    session.chunkSizeBytes(),
                    session.totalChunks(),
                    session.completedParts(),
                    session.updatedAt(),
                    session.uploadMode(),
                    session.providerSessionId()
            );
        }
    }

    /** 上传能力响应。 */
    public record UploadCapabilityResponse(boolean supportsDirectUpload,
                                           String provider,
                                           long maxPartSizeBytes,
                                           long suggestedChunkSizeBytes) {
        static UploadCapabilityResponse from(TransferDirectUploadService.UploadCapability capability) {
            return new UploadCapabilityResponse(
                    capability.supportsDirectUpload(),
                    capability.provider(),
                    capability.maxPartSizeBytes(),
                    capability.suggestedChunkSizeBytes()
            );
        }
    }

    /** 直传票据响应。 */
    public record DirectTicketResponse(String taskId,
                                       String uploadId,
                                       String providerSessionId,
                                       long chunkSizeBytes,
                                       int totalChunks,
                                       java.util.List<DirectPartResponse> parts) {
        static DirectTicketResponse from(TransferDirectUploadService.DirectUploadTicket ticket) {
            return new DirectTicketResponse(
                    ticket.taskId(),
                    ticket.uploadId(),
                    ticket.providerSessionId(),
                    ticket.chunkSizeBytes(),
                    ticket.totalChunks(),
                    ticket.parts().stream()
                            .map(it -> new DirectPartResponse(it.partNumber(), it.uploadUrl(), it.token()))
                            .toList()
            );
        }
    }

    /** 直传分片响应项。 */
    public record DirectPartResponse(int partNumber, String uploadUrl, String token) { }

}
