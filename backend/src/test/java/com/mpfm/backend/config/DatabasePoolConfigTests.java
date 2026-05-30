package com.mpfm.backend.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class DatabasePoolConfigTests {

    @Test
    void devProfileShouldDeclareExplicitHikariPoolSettings() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        var properties = yaml.getObject();

        assertNotNull(properties);
        assertNotNull(properties.getProperty("spring.datasource.hikari.minimum-idle"));
        assertNotNull(properties.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertNotNull(properties.getProperty("spring.datasource.hikari.auto-commit"));
        assertNotNull(properties.getProperty("spring.datasource.hikari.transaction-isolation"));
    }
}
