package com.mpfm.backend;

import com.mpfm.backend.application.user.AvatarStorageProperties;
import com.mpfm.backend.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 后端应用启动入口，负责引导 Spring Boot 应用初始化。
 */
@EnableRetry
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AvatarStorageProperties.class})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}




