// src/main/java/com/clickkart/gateway/config/SecurityConfig.java
package com.clickkart.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security here only guards this service's own actuator endpoints. All business
 * traffic passing through the gateway is authenticated by JwtAuthenticationGlobalFilter
 * as part of the Gateway filter chain, not by this reactive security chain - so every
 * non-actuator exchange is explicitly permitAll() here to avoid two competing auth
 * mechanisms on the same request.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/**").authenticated()
                        .anyExchange().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
