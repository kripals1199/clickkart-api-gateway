// src/test/java/com/clickkart/gateway/filter/JwtAuthenticationGlobalFilterTest.java
package com.clickkart.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.gateway.config.GatewaySecurityProperties;
import com.clickkart.gateway.exception.ErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";

    private JwtAuthenticationGlobalFilter filter;
    private GatewayFilterChain chain;
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    private GatewaySecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GatewaySecurityProperties();
        properties.setJwtSecret(SECRET);
        properties.setPublicPaths(List.of("/api/v1/auth/login", "/actuator/health"));

        reactiveStringRedisTemplate = mock(ReactiveStringRedisTemplate.class);
        // not revoked by default; individual tests override for the revoked-token case
        when(reactiveStringRedisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        filter = new JwtAuthenticationGlobalFilter(
                new JwtValidator(properties),
                properties,
                new ErrorResponseWriter(new com.fasterxml.jackson.databind.ObjectMapper()),
                reactiveStringRedisTemplate);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String signToken(String subject, String correlationId, String jti) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(jti)
                .subject(subject)
                .claim(JwtClaimNames.ROLES, "CUSTOMER")
                .claim(JwtClaimNames.CORRELATION_ID, correlationId)
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    @Test
    void publicPathsPassThroughWithoutRequiringAuthorization() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedPathWithoutAuthorizationHeaderIsRejected() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, times(0)).filter(any());
    }

    @Test
    void protectedPathWithValidTokenPassesThroughWithForwardedHeaders() {
        String token = signToken("user-42", "corr-abc-123", UUID.randomUUID().toString());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void protectedPathWithTamperedTokenIsRejected() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("Authorization", "Bearer not-a-real-token")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, times(0)).filter(any());
    }

    @Test
    void protectedPathWithRevokedTokenIsRejected() {
        String jti = UUID.randomUUID().toString();
        String token = signToken("user-42", "corr-abc-123", jti);
        when(reactiveStringRedisTemplate.hasKey(eq(properties.getRevocationKeyPrefix() + jti))).thenReturn(Mono.just(true));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, times(0)).filter(any());
    }
}
