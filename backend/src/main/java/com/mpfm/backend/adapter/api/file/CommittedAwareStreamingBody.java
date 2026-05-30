package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 流式响应异常包装器：当响应已提交时仅记录并终止流；未提交时抛业务异常交由统一异常处理。
 */
final class CommittedAwareStreamingBody implements StreamingResponseBody {
    @FunctionalInterface
    interface StreamWriter {
        void write(OutputStream outputStream) throws Exception;
    }

    private final StreamWriter delegate;
    private final BiConsumer<String, Throwable> committedLogger;
    private final String path;

    private CommittedAwareStreamingBody(StreamWriter delegate,
                                        BiConsumer<String, Throwable> committedLogger,
                                        String path) {
        this.delegate = delegate;
        this.committedLogger = committedLogger;
        this.path = path;
    }

    static StreamingResponseBody wrap(StreamWriter delegate,
                                      BiConsumer<String, Throwable> committedLogger,
                                      String path) {
        return new CommittedAwareStreamingBody(delegate, committedLogger, path);
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        try {
            delegate.write(outputStream);
        } catch (Exception ex) {
            if (isResponseCommitted()) {
                committedLogger.accept(path, ex);
                return;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "streaming response failed", ex);
        }
    }

    private boolean isResponseCommitted() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return true;
        }
        return servletAttrs.getResponse() == null || servletAttrs.getResponse().isCommitted();
    }
}

