package com.mpfm.backend.application.user;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mpfm.avatar")
public record AvatarStorageProperties(
        @NotBlank String basePath,
        @NotBlank String signingKey,
        long signedUrlExpireSeconds
) {
}
