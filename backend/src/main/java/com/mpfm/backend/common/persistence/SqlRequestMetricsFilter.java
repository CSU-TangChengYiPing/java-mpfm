package com.mpfm.backend.common.persistence;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求级 SQL 计数过滤器：输出每个请求的 SQL 次数与最慢耗时，辅助定位高频查询问题。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class SqlRequestMetricsFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SqlRequestMetricsFilter.class);
    private final SqlObservabilityProperties properties;

    public SqlRequestMetricsFilter(ObjectProvider<SqlObservabilityProperties> propertiesProvider) {
        this.properties = propertiesProvider.getIfAvailable(SqlObservabilityProperties::new);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        SqlRequestMetricsContext.begin();
        try {
            filterChain.doFilter(request, response);
        } finally {
            SqlRequestMetricsContext.Metrics metrics = SqlRequestMetricsContext.end();
            if (metrics == null) {
                return;
            }
            int sqlCount = metrics.sqlCount().get();
            long totalMs = metrics.totalElapsedMillis().get();
            long maxMs = metrics.maxElapsedMillis().get();
            if (sqlCount <= 0) {
                return;
            }
            String message = "request_sql_metrics method={} path={} status={} sqlCount={} totalSqlMs={} maxSqlMs={}";
            if (sqlCount >= properties.getRequestErrorSqlCount()) {
                log.error(message, request.getMethod(), request.getRequestURI(), response.getStatus(), sqlCount, totalMs, maxMs);
            } else if (sqlCount >= properties.getRequestWarnSqlCount()) {
                log.warn(message, request.getMethod(), request.getRequestURI(), response.getStatus(), sqlCount, totalMs, maxMs);
            } else if (sqlCount >= properties.getRequestInfoSqlCount()) {
                log.info(message, request.getMethod(), request.getRequestURI(), response.getStatus(), sqlCount, totalMs, maxMs);
            }
        }
    }
}
