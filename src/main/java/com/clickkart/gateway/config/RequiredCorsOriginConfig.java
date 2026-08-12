// src/main/java/com/clickkart/gateway/config/RequiredCorsOriginConfig.java
package com.clickkart.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * spring.cloud.gateway.server.webflux.globalcors.cors-configurations binds into a
 * Map<String, CorsConfiguration>, the same kind of property Spring's Binder does not
 * strictly validate for unresolved placeholders - see RequiredEurekaClientConfig in this
 * package for the same class of issue. ALLOWED_ORIGINS has no default in qa/prod.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"qa", "prod"})
class RequiredCorsOriginConfig {

    RequiredCorsOriginConfig(@Value("${ALLOWED_ORIGINS}") String allowedOrigins) {
        RequiredEurekaCredentialsConfig.require(allowedOrigins, "ALLOWED_ORIGINS");
    }
}
