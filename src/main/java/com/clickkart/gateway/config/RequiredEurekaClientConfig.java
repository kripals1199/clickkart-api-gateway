// src/main/java/com/clickkart/gateway/config/RequiredEurekaClientConfig.java
package com.clickkart.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * eureka.client.service-url.defaultZone binds into a Map<String,String>, which Spring's
 * relaxed Binder does not validate for unresolved placeholders the way scalar properties
 * are validated - see clickkart-eureka-server's RequiredProdSecretsConfig and
 * clickkart-config-server's RequiredEurekaClientConfig for the same issue found earlier in
 * this build. EUREKA_DASHBOARD_USERNAME/PASSWORD are true secrets with no default in
 * test/qa/prod. EUREKA_SERVER_HOST has a profile-specific default in test/qa and is only
 * strictly required in prod - validated separately below.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"test", "qa", "prod"})
class RequiredEurekaCredentialsConfig {

    RequiredEurekaCredentialsConfig(
            @Value("${EUREKA_DASHBOARD_USERNAME}") String eurekaDashboardUsername,
            @Value("${EUREKA_DASHBOARD_PASSWORD}") String eurekaDashboardPassword) {
        require(eurekaDashboardUsername, "EUREKA_DASHBOARD_USERNAME");
        require(eurekaDashboardPassword, "EUREKA_DASHBOARD_PASSWORD");
    }

    static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class RequiredProdEurekaHostConfig {

    RequiredProdEurekaHostConfig(@Value("${EUREKA_SERVER_HOST}") String eurekaServerHost) {
        RequiredEurekaCredentialsConfig.require(eurekaServerHost, "EUREKA_SERVER_HOST");
    }
}
