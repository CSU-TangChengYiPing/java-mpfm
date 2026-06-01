package com.mpfm.backend.common.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SQL 可观测配置：定义慢查询阈值与请求级 SQL 计数阈值。
 */
@ConfigurationProperties(prefix = "mpfm.sql-observability")
@Component
public class SqlObservabilityProperties {
    private boolean enabled = true;
    private long slowInfoMs = 150L;
    private long slowWarnMs = 400L;
    private long slowErrorMs = 1000L;
    private int requestInfoSqlCount = 8;
    private int requestWarnSqlCount = 40;
    private int requestErrorSqlCount = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowInfoMs() {
        return slowInfoMs;
    }

    public void setSlowInfoMs(long slowInfoMs) {
        this.slowInfoMs = slowInfoMs;
    }

    public long getSlowWarnMs() {
        return slowWarnMs;
    }

    public void setSlowWarnMs(long slowWarnMs) {
        this.slowWarnMs = slowWarnMs;
    }

    public long getSlowErrorMs() {
        return slowErrorMs;
    }

    public void setSlowErrorMs(long slowErrorMs) {
        this.slowErrorMs = slowErrorMs;
    }

    public int getRequestWarnSqlCount() {
        return requestWarnSqlCount;
    }

    public void setRequestWarnSqlCount(int requestWarnSqlCount) {
        this.requestWarnSqlCount = requestWarnSqlCount;
    }

    public int getRequestErrorSqlCount() {
        return requestErrorSqlCount;
    }

    public void setRequestErrorSqlCount(int requestErrorSqlCount) {
        this.requestErrorSqlCount = requestErrorSqlCount;
    }

    public int getRequestInfoSqlCount() {
        return requestInfoSqlCount;
    }

    public void setRequestInfoSqlCount(int requestInfoSqlCount) {
        this.requestInfoSqlCount = requestInfoSqlCount;
    }
}
