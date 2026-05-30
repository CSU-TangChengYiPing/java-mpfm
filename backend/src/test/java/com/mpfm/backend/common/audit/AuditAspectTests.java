package com.mpfm.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mpfm.backend.application.audit.DemoAuditService;
import com.mpfm.backend.common.error.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@ContextConfiguration(classes = AuditAspectTests.TestConfig.class)
class AuditAspectTests {

    @Autowired
    private DemoAuditService demoAuditService;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setup() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @Test
    void shouldWriteSuccessAuditLog() {
        demoAuditService.success();

        assertThat(appender.list)
                .anyMatch(event -> event.getLevel() == Level.INFO
                        && event.getFormattedMessage().contains("action=demo_success")
                        && event.getFormattedMessage().contains("result=success"));
    }

    @Test
    void shouldWriteFailureAuditLog() {
        assertThatThrownBy(() -> demoAuditService.failure()).isInstanceOf(BusinessException.class);

        assertThat(appender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("action=demo_failure")
                        && event.getFormattedMessage().contains("result=failure"));
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect(null, securityEventLogger());
        }

        @Bean
        SecurityEventLogger securityEventLogger() {
            return new SecurityEventLogger(null, new SimpleMeterRegistry());
        }

        @Bean
        DemoAuditService demoAuditService() {
            return new DemoAuditService();
        }
    }
}

