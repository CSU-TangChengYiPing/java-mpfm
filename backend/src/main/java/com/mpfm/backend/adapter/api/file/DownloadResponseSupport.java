package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverLink;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 下载响应统一支持组件：把 local/sftp/webdav 分支从 Controller 下沉到单一执行入口。
 */
@Component
class DownloadResponseSupport {
    private static final Logger log = LoggerFactory.getLogger(DownloadResponseSupport.class);
    private static final String INVALID_RANGE_HEADER = "invalid range header";
    private static final String RANGE_HEADER = "Range";
    private static final String IF_RANGE_HEADER = "If-Range";
    private static final String BYTES_PREFIX = "bytes=";

    private final DriverFactory driverFactory;
    private final TransferTelemetryService transferTelemetryService;
    private final TransferBandwidthLimiter transferBandwidthLimiter;

    DownloadResponseSupport(DriverFactory driverFactory,
                            TransferTelemetryService transferTelemetryService,
                            TransferBandwidthLimiter transferBandwidthLimiter) {
        this.driverFactory = driverFactory;
        this.transferTelemetryService = transferTelemetryService;
        this.transferBandwidthLimiter = transferBandwidthLimiter;
    }

    void validateRange(String header) {
        if (header == null || header.isBlank()) {
            return;
        }
        if (!header.matches("^bytes=(\\d+)-(\\d*)$")) {
            throw new BusinessException(ErrorCode.RANGE_INVALID, INVALID_RANGE_HEADER);
        }
        String payload = header.substring(BYTES_PREFIX.length());
        String[] parts = payload.split("-", 2);
        long start = Long.parseLong(parts[0]);
        long end = parts[1].isBlank() ? start : Long.parseLong(parts[1]);
        if (start < 0 || end < start) {
            throw new BusinessException(ErrorCode.RANGE_INVALID, INVALID_RANGE_HEADER);
        }
    }

    ResponseEntity<StreamingResponseBody> download(String endpointPath,
                                                   String username,
                                                   NamespaceResolver.ResolveResult resolved,
                                                   String etag,
                                                   String mtimeIso,
                                                   String rangeHeader,
                                                   String ifRangeHeader) {
        if ("sftp".equalsIgnoreCase(resolved.mount().getType())) {
            return downloadFromSftp(endpointPath, username, resolved, etag, mtimeIso, rangeHeader, ifRangeHeader);
        }
        if (!"local".equalsIgnoreCase(resolved.mount().getType())) {
            return downloadFromRemoteDriver(endpointPath, username, resolved, etag, mtimeIso, rangeHeader, ifRangeHeader);
        }
        return downloadFromLocal(endpointPath, username, resolved, etag, mtimeIso, rangeHeader, ifRangeHeader);
    }

    private ResponseEntity<StreamingResponseBody> downloadFromLocal(String endpointPath,
                                                                    String username,
                                                                    NamespaceResolver.ResolveResult resolved,
                                                                    String etag,
                                                                    String mtimeIso,
                                                                    String rangeHeader,
                                                                    String ifRangeHeader) {
        Path root = Path.of(resolved.mount().getPhysicalRoot()).normalize();
        Path target = ".".equals(resolved.relPath()) ? root : root.resolve(resolved.relPath()).normalize();
        if (!target.startsWith(root) || !Files.exists(target) || Files.isDirectory(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "download file not found");
        }
        long totalBytes;
        try {
            totalBytes = Files.size(target);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read download file size failed", ex);
        }
        Optional<RangeWindow> rangeWindow = resolveRangeWindow(totalBytes, rangeHeader, ifRangeHeader, etag);
        long start = rangeWindow.map(RangeWindow::start).orElse(0L);
        long end = rangeWindow.map(RangeWindow::end).orElse(Math.max(0L, totalBytes - 1L));
        long length = totalBytes == 0L ? 0L : Math.max(0L, end - start + 1L);
        HttpStatus status = rangeWindow.isPresent() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        HttpHeaders headers = buildDownloadHeaders(etag, mtimeIso, length);
        String filename = target.getFileName() == null ? "download.bin" : target.getFileName().toString();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename));
        if (rangeWindow.isPresent()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalBytes);
        }
        StreamingResponseBody body = CommittedAwareStreamingBody.wrap(
                outputStream -> streamFileRange(target, username, start, length, outputStream),
                (path, ex) -> log.warn("stream aborted after response committed path={} message={}", path, ex.toString()),
                endpointPath);
        return new ResponseEntity<>(body, headers, status);
    }

    private ResponseEntity<StreamingResponseBody> downloadFromSftp(String endpointPath,
                                                                   String username,
                                                                   NamespaceResolver.ResolveResult resolved,
                                                                   String etag,
                                                                   String mtimeIso,
                                                                   String rangeHeader,
                                                                   String ifRangeHeader) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(new DriverContext(username, resolved.mount()));
        try {
            String target = toSftpTargetPath(connection.basePath(), resolved.relPath());
            SftpClient.Attributes attrs = connection.sftpClient().stat(target);
            if (attrs.isDirectory()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "download file not found");
            }
            long totalBytes = attrs.getSize();
            Optional<RangeWindow> rangeWindow = resolveRangeWindow(totalBytes, rangeHeader, ifRangeHeader, etag);
            long start = rangeWindow.map(RangeWindow::start).orElse(0L);
            long end = rangeWindow.map(RangeWindow::end).orElse(Math.max(0L, totalBytes - 1L));
            long length = totalBytes == 0L ? 0L : Math.max(0L, end - start + 1L);
            HttpStatus status = rangeWindow.isPresent() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
            HttpHeaders headers = buildDownloadHeaders(etag, mtimeIso, length);
            String filename = target.substring(target.lastIndexOf('/') + 1);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename));
            if (rangeWindow.isPresent()) {
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalBytes);
            }
            StreamingResponseBody body = CommittedAwareStreamingBody.wrap(
                    outputStream -> streamSftpFileRange(connection, target, username, start, length, outputStream),
                    (path, ex) -> log.warn("stream aborted after response committed path={} message={}", path, ex.toString()),
                    endpointPath);
            return new ResponseEntity<>(body, headers, status);
        } catch (BusinessException ex) {
            SftpDriverUtil.closeQuietly(connection);
            throw ex;
        } catch (Exception ex) {
            SftpDriverUtil.closeQuietly(connection);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp download failed", ex);
        }
    }

    private ResponseEntity<StreamingResponseBody> downloadFromRemoteDriver(String endpointPath,
                                                                           String username,
                                                                           NamespaceResolver.ResolveResult resolved,
                                                                           String etag,
                                                                           String mtimeIso,
                                                                           String rangeHeader,
                                                                           String ifRangeHeader) {
        DriverLink link = driverFactory.resolve(resolved.mount().getType())
                .link(new DriverContext(username, resolved.mount()), resolved.relPath());
        HttpURLConnection probe = null;
        try {
            probe = openRemoteGet(link, rangeHeader, ifRangeHeader, etag);
            int statusCode = probe.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "download file not found");
            }
            if (statusCode >= 400) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "remote download failed, status=" + statusCode);
            }
            HttpHeaders headers = buildDownloadHeaders(etag, mtimeIso, contentLength(probe));
            String contentRange = probe.getHeaderField(HttpHeaders.CONTENT_RANGE);
            HttpStatus responseStatus = (statusCode == HttpURLConnection.HTTP_PARTIAL) ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
            if (contentRange != null && !contentRange.isBlank()) {
                headers.set(HttpHeaders.CONTENT_RANGE, contentRange);
            }
            String filename = resolved.relPath().contains("/") ? resolved.relPath().substring(resolved.relPath().lastIndexOf('/') + 1) : resolved.relPath();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename.isBlank() ? "download.bin" : filename));
            HttpURLConnection streamConnection = probe;
            StreamingResponseBody body = CommittedAwareStreamingBody.wrap(
                    outputStream -> streamHttpConnection(streamConnection, username, outputStream),
                    (path, ex) -> log.warn("stream aborted after response committed path={} message={}", path, ex.toString()),
                    endpointPath);
            return new ResponseEntity<>(body, headers, responseStatus);
        } catch (BusinessException ex) {
            if (probe != null) {
                probe.disconnect();
            }
            throw ex;
        } catch (Exception ex) {
            if (probe != null) {
                probe.disconnect();
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "remote download failed", ex);
        }
    }

    private Optional<RangeWindow> resolveRangeWindow(long sourceLength, String rangeHeader, String ifRange, String etag) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Optional.empty();
        }
        if (ifRange != null && !ifRange.isBlank() && !ifRange.equals(etag)) {
            return Optional.empty();
        }
        String payload = rangeHeader.substring(BYTES_PREFIX.length());
        String[] parts = payload.split("-", 2);
        long start = Long.parseLong(parts[0]);
        long requestedEnd = parts[1].isBlank() ? sourceLength - 1 : Long.parseLong(parts[1]);
        if (start >= sourceLength) {
            throw new BusinessException(ErrorCode.RANGE_INVALID, INVALID_RANGE_HEADER);
        }
        long end = Math.min(requestedEnd, sourceLength - 1);
        return Optional.of(new RangeWindow(start, end));
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
            // ignore
        }
        return headers;
    }

    private HttpURLConnection openRemoteGet(DriverLink link, String rangeHeader, String ifRangeHeader, String etag) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(link.url()).toURL().openConnection();
        connection.setRequestMethod("GET");
        if (link.headers() != null) {
            link.headers().forEach(connection::setRequestProperty);
        }
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            connection.setRequestProperty(RANGE_HEADER, rangeHeader);
        }
        if (ifRangeHeader != null && !ifRangeHeader.isBlank() && ifRangeHeader.equals(etag)) {
            connection.setRequestProperty(IF_RANGE_HEADER, ifRangeHeader);
        }
        return connection;
    }

    private void streamHttpConnection(HttpURLConnection connection, String username, OutputStream outputStream) throws IOException {
        try (InputStream inputStream = connection.getInputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                transferBandwidthLimiter.awaitDownloadPermit(username, read);
                transferTelemetryService.recordLiveDownload(username, read);
            }
            outputStream.flush();
        } finally {
            connection.disconnect();
        }
    }

    private long contentLength(HttpURLConnection connection) {
        String value = connection.getHeaderField(HttpHeaders.CONTENT_LENGTH);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void streamFileRange(Path target, String username, long start, long length, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream inputStream = Files.newInputStream(target)) {
            skipFully(inputStream, start);
            long remaining = length;
            while (remaining > 0) {
                int expected = (int) Math.min(buffer.length, remaining);
                int read = inputStream.read(buffer, 0, expected);
                if (read < 0) {
                    break;
                }
                transferBandwidthLimiter.awaitDownloadPermit(username, read);
                outputStream.write(buffer, 0, read);
                transferTelemetryService.recordLiveDownload(username, read);
                remaining -= read;
            }
            outputStream.flush();
        }
    }

    private void skipFully(InputStream inputStream, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                int read = inputStream.read();
                if (read < 0) {
                    throw new BusinessException(ErrorCode.RANGE_INVALID, INVALID_RANGE_HEADER);
                }
                skipped = 1L;
            }
            remaining -= skipped;
        }
    }

    private void streamSftpFileRange(SftpDriverUtil.SftpConnection connection,
                                     String target,
                                     String username,
                                     long start,
                                     long length,
                                     OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long offset = start;
        long remaining = length;
        try (SftpClient.CloseableHandle handle = connection.sftpClient().open(target, SftpClient.OpenMode.Read)) {
            while (remaining > 0) {
                int expected = (int) Math.min(buffer.length, remaining);
                int read = connection.sftpClient().read(handle, offset, buffer, 0, expected);
                if (read <= 0) {
                    break;
                }
                transferBandwidthLimiter.awaitDownloadPermit(username, read);
                outputStream.write(buffer, 0, read);
                transferTelemetryService.recordLiveDownload(username, read);
                remaining -= read;
                offset += read;
            }
            outputStream.flush();
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

    private String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record RangeWindow(long start, long end) {
    }
}
