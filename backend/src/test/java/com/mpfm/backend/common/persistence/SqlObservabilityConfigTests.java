package com.mpfm.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlObservabilityConfigTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDataSourceConfig.class, SqlObservabilityConfig.class)
            .withPropertyValues(
                    "mpfm.sql-observability.enabled=true",
                    "mpfm.sql-observability.slow-info-ms=1",
                    "mpfm.sql-observability.slow-warn-ms=2",
                    "mpfm.sql-observability.slow-error-ms=3");

    @Test
    void shouldKeepPrimaryDataSourceUnwrappedAndExposeProxyForJdbcTemplate() {
        contextRunner.run(context -> {
            DataSource rawDataSource = context.getBean("dataSource", DataSource.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertThat(rawDataSource).isInstanceOf(HikariDataSource.class);
            assertThat(context.containsBean("sqlObservabilityDataSource")).isFalse();
            assertThat(jdbcTemplate.getDataSource()).isNotSameAs(rawDataSource);
            assertThat(jdbcTemplate.getDataSource().getClass().getName()).contains("ProxyDataSource");
            assertThat(context.getBean("dataSource", DataSource.class)).isSameAs(rawDataSource);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDataSourceConfig {
        @Bean
        DataSource dataSource() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/mpfm_dev");
            dataSource.setUsername("postgres");
            dataSource.setPassword("postgres");
            dataSource.setPoolName("test-hikari");
            dataSource.setMinimumIdle(1);
            dataSource.setMaximumPoolSize(5);
            dataSource.setAutoCommit(false);
            return dataSource;
        }
    }
}
