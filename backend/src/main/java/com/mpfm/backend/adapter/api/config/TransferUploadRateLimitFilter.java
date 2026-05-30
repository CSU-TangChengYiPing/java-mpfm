package com.mpfm.backend.adapter.api.config;

import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 上传入口限速过滤器：按 OpenList 的请求体读取整形语义，在 read() 时执行 WaitN。
 */
@Component
public class TransferUploadRateLimitFilter extends OncePerRequestFilter {
    private static final String RUNTIME_UPLOAD_TASKS_URI = "/api/v4/transfers/uploads/runtime/tasks";
    private static final String DIRECT_PARTS_URI_PREFIX = "/api/v4/transfers/uploads/direct/";
    private static final String DIRECT_PARTS_URI_SUFFIX = "/parts/";
    private final TransferBandwidthLimiter transferBandwidthLimiter;

    public TransferUploadRateLimitFilter(@Nullable TransferBandwidthLimiter transferBandwidthLimiter) {
        this.transferBandwidthLimiter = transferBandwidthLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String username = resolveUsername(request);
        if (transferBandwidthLimiter == null || username == null || username.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpServletRequest wrapped = new UploadRateLimitRequestWrapper(request, username, transferBandwidthLimiter);
        filterChain.doFilter(wrapped, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) && uri.endsWith(RUNTIME_UPLOAD_TASKS_URI)) {
            return false;
        }
        return !("PUT".equalsIgnoreCase(method)
                && uri.contains(DIRECT_PARTS_URI_PREFIX)
                && uri.contains(DIRECT_PARTS_URI_SUFFIX));
    }

    private String resolveUsername(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return null;
    }

    /**
     * 请求包装器：把原始输入流替换成带上传令牌等待的输入流。
     */
    private static final class UploadRateLimitRequestWrapper extends HttpServletRequestWrapper {
        private final String username;
        private final TransferBandwidthLimiter limiter;
        private ServletInputStream cachedInputStream;

        private UploadRateLimitRequestWrapper(HttpServletRequest request, String username, TransferBandwidthLimiter limiter) {
            super(request);
            this.username = username;
            this.limiter = limiter;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (cachedInputStream == null) {
                cachedInputStream = new UploadRateLimitServletInputStream(super.getInputStream(), username, limiter);
            }
            return cachedInputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /**
     * 输入流包装器：每次读取后基于实际字节数执行上传令牌等待。
     */
    private static final class UploadRateLimitServletInputStream extends ServletInputStream {
        private static final int AWAIT_CHUNK_BYTES = 64 * 1024;
        private final ServletInputStream delegate;
        private final String username;
        private final TransferBandwidthLimiter limiter;
        private int pendingBytes;

        private UploadRateLimitServletInputStream(ServletInputStream delegate, String username, TransferBandwidthLimiter limiter) {
            this.delegate = delegate;
            this.username = username;
            this.limiter = limiter;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                pendingBytes += 1;
                flushPendingIfNeeded(false);
            } else {
                flushPendingIfNeeded(true);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                pendingBytes += read;
                flushPendingIfNeeded(false);
            } else if (read < 0) {
                flushPendingIfNeeded(true);
            }
            return read;
        }

        private void flushPendingIfNeeded(boolean force) {
            if (pendingBytes <= 0) {
                return;
            }
            if (!force && pendingBytes < AWAIT_CHUNK_BYTES) {
                return;
            }
            limiter.awaitUploadPermit(username, pendingBytes);
            pendingBytes = 0;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
