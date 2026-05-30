package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 文件查询控制器，提供树/list/stat 与内容读取接口；下载能力委托给统一下载支持组件。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileQueryController {
    private static final String RANGE_HEADER = "Range";
    private static final String IF_RANGE_HEADER = "If-Range";

    private final FileApplicationService fileApplicationService;
    private final NamespaceResolver namespaceResolver;
    private final DownloadResponseSupport downloadResponseSupport;
    private final UserTransferGovernanceService userTransferGovernanceService;
    private final TransferTelemetryService transferTelemetryService;
    private final TransferBandwidthLimiter transferBandwidthLimiter;

    public FileQueryController(FileApplicationService fileApplicationService,
                               NamespaceResolver namespaceResolver,
                               DownloadResponseSupport downloadResponseSupport,
                               UserTransferGovernanceService userTransferGovernanceService,
                               TransferTelemetryService transferTelemetryService,
                               TransferBandwidthLimiter transferBandwidthLimiter) {
        this.fileApplicationService = fileApplicationService;
        this.namespaceResolver = namespaceResolver;
        this.downloadResponseSupport = downloadResponseSupport;
        this.userTransferGovernanceService = userTransferGovernanceService;
        this.transferTelemetryService = transferTelemetryService;
        this.transferBandwidthLimiter = transferBandwidthLimiter;
    }

    @GetMapping("/tree")
    public FileApiModels.FileItemsResponse tree(@RequestParam String virtualPath, Principal principal) {
        List<FileApiModels.FileEntryResponse> items = fileApplicationService.tree(principal.getName(), virtualPath)
                .stream().map(FileApiModels.FileEntryResponse::from).toList();
        return new FileApiModels.FileItemsResponse(items, new FileApiModels.PageMeta(1, items.size(), items.size()));
    }

    @GetMapping("/list")
    public FileApiModels.FileItemsResponse list(@RequestParam String virtualPath, Principal principal) {
        List<FileApiModels.FileEntryResponse> items = fileApplicationService.list(principal.getName(), virtualPath)
                .stream().map(FileApiModels.FileEntryResponse::from).toList();
        return new FileApiModels.FileItemsResponse(items, new FileApiModels.PageMeta(1, items.size(), items.size()));
    }

    @GetMapping("/stat")
    public FileApiModels.FileEntryEnvelope stat(@RequestParam String virtualPath, Principal principal) {
        return new FileApiModels.FileEntryEnvelope(
                FileApiModels.FileEntryResponse.from(fileApplicationService.stat(principal.getName(), virtualPath)));
    }

    @GetMapping("/content")
    public ResponseEntity<?> read(@RequestParam String virtualPath,
                                  @RequestParam(required = false) String grantId,
                                  @RequestParam(required = false, defaultValue = "false") boolean raw,
                                  HttpServletRequest request,
                                  Principal principal) {
        userTransferGovernanceService.ensureDownloadAllowed(principal.getName());
        downloadResponseSupport.validateRange(request.getHeader(RANGE_HEADER));
        FileApiModels.FileEntryResponse stat = FileApiModels.FileEntryResponse.from(fileApplicationService.stat(principal.getName(), virtualPath));
        if (!raw) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ETAG, stat.etag())
                    .body(new FileApiModels.FileContentEnvelope(stat, fileApplicationService.readFile(principal.getName(), virtualPath)));
        }
        byte[] bytes = fileApplicationService.readFileBytes(principal.getName(), virtualPath);
        return streamResponse(bytes, request.getHeader(RANGE_HEADER), request.getHeader(IF_RANGE_HEADER), stat.etag(), stat.mtime(), principal.getName());
    }

    @GetMapping("/preview")
    public FileApiModels.FileContentResponse preview(@RequestParam String virtualPath, Principal principal) {
        return new FileApiModels.FileContentResponse(virtualPath, fileApplicationService.readFile(principal.getName(), virtualPath));
    }

    /**
     * 下载主链路：控制器不关心协议细节，统一委托下载支持组件按能力分发。
     */
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam String virtualPath,
                                                          HttpServletRequest request,
                                                          Principal principal) {
        userTransferGovernanceService.ensureDownloadAllowed(principal.getName());
        downloadResponseSupport.validateRange(request.getHeader(RANGE_HEADER));
        FileApiModels.FileEntryResponse stat = FileApiModels.FileEntryResponse.from(
                fileApplicationService.stat(principal.getName(), virtualPath));
        NamespaceResolver.ResolveResult resolved = namespaceResolver.resolve(principal.getName(), virtualPath, false, true);
        return downloadResponseSupport.download(
                "/api/v1/files/download",
                principal.getName(),
                resolved,
                stat.etag(),
                stat.mtime(),
                request.getHeader(RANGE_HEADER),
                request.getHeader(IF_RANGE_HEADER));
    }

    private ResponseEntity<byte[]> streamResponse(byte[] source,
                                                  String rangeHeader,
                                                  String ifRange,
                                                  String etag,
                                                  String mtimeIso,
                                                  String username) {
        int start = 0;
        int end = source.length == 0 ? -1 : source.length - 1;
        boolean partial = false;
        if (rangeHeader != null && !rangeHeader.isBlank() && (ifRange == null || ifRange.isBlank() || ifRange.equals(etag))) {
            String payload = rangeHeader.substring("bytes=".length());
            String[] parts = payload.split("-", 2);
            long requestStart = Long.parseLong(parts[0]);
            long requestEnd = parts[1].isBlank() ? source.length - 1L : Long.parseLong(parts[1]);
            if (requestStart >= source.length) {
                throw new com.mpfm.backend.common.error.BusinessException(com.mpfm.backend.common.error.ErrorCode.RANGE_INVALID, "invalid range header");
            }
            start = (int) requestStart;
            end = (int) Math.min(requestEnd, source.length - 1L);
            partial = true;
        }
        int length = end < start ? 0 : (end - start + 1);
        byte[] body = new byte[length];
        if (length > 0) {
            System.arraycopy(source, start, body, 0, length);
            transferBandwidthLimiter.awaitDownloadPermit(username, length);
            transferTelemetryService.recordLiveDownload(username, length);
        }
        HttpHeaders headers = buildDownloadHeaders(etag, mtimeIso, length);
        if (partial) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + source.length);
        }
        return new ResponseEntity<>(body, headers, partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    private HttpHeaders buildDownloadHeaders(String etag, String mtimeIso, long length) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.ETAG, etag);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(length);
        try {
            headers.set(HttpHeaders.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.parse(mtimeIso)));
        } catch (Exception ignored) {
            // mtime 解析失败时不阻断读取。
        }
        return headers;
    }
}
