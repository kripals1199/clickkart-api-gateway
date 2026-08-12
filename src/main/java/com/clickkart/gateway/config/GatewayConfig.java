// src/main/java/com/clickkart/gateway/config/GatewayConfig.java
package com.clickkart.gateway.config;

import com.clickkart.gateway.filter.JwtAuthenticationGlobalFilter;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewayConfig {

    /**
     * Rate-limits authenticated traffic per user (X-User-Id, set by
     * JwtAuthenticationGlobalFilter) and falls back to the caller's IP for
     * unauthenticated/public routes such as login.
     */
    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown");
            return Mono.just(ip);
        };
    }
}
