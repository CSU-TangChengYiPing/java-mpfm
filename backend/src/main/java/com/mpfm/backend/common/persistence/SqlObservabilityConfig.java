package com.mpfm.backend.common.persistence;

import java.util.List;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL 可观测装配：保留原始数据源给 JPA/Hibernate 识别连接池信息，单独给 JDBC 查询链路挂代理。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlObservabilityProperties.class)
public class SqlObservabilityConfig {
    private static final Logger log = LoggerFactory.getLogger(SqlObservabilityConfig.class);

    /**
     * JDBC 模板只认代理数据源，JPA/Hibernate 保持使用 Boot 自动配置的原始数据源。
     * 代理只挂在模板层，避免把它注册成 `DataSource` Bean 之后抢走 Flyway/JPA 的基础设施装配。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource, SqlObservabilityProperties properties) {
        return new JdbcTemplate(sqlObservabilityDataSource(dataSource, properties));
    }

    private DataSource sqlObservabilityDataSource(DataSource dataSource, SqlObservabilityProperties properties) {
        if (!properties.isEnabled()) {
            return dataSource;
        }
        return ProxyDataSourceBuilder.create(dataSource)
                .name("mpfm-sql-observe")
                .listener(new QueryExecutionListener() {
                    @Override
                    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
                        // 仅在查询结束后统计耗时，避免把执行前的排队时间误记为慢 SQL。
                    }

                    @Override
                    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
                        long elapsedMs = Math.max(0L, execInfo.getElapsedTime());
                        SqlRequestMetricsContext.record(elapsedMs);
                        if (elapsedMs >= properties.getSlowErrorMs()) {
                            log.error("slow_sql level=error elapsedMs={} queryCount={} success={}",
                                    elapsedMs, queryInfoList.size(), execInfo.isSuccess());
                        } else if (elapsedMs >= properties.getSlowWarnMs()) {
                            log.warn("slow_sql level=warn elapsedMs={} queryCount={} success={}",
                                    elapsedMs, queryInfoList.size(), execInfo.isSuccess());
                        } else if (elapsedMs >= properties.getSlowInfoMs()) {
                            log.info("slow_sql level=info elapsedMs={} queryCount={} success={}",
                                    elapsedMs, queryInfoList.size(), execInfo.isSuccess());
                        }
                    }
                })
                .build();
    }
}
