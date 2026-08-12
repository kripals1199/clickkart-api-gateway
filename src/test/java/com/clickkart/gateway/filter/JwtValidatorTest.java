// src/test/java/com/clickkart/gateway/filter/JwtValidatorTest.java
package com.clickkart.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clickkart.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtValidatorTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";

    private JwtValidator newValidator() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setJwtSecret(SECRET);
        return new JwtValidator(properties);
    }

    private String signToken(String secret, String subject, String correlationId, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim(JwtClaimNames.ROLES, "CUSTOMER")
                .claim(JwtClaimNames.CORRELATION_ID, correlationId)
                .issuedAt(Date.from(Instant.now()))
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    @Test
    void validatesAndExtractsClaimsFromAGenuineToken() {
        JwtValidator validator = newValidator();
        String token = signToken(SECRET, "user-42", "corr-abc-123", Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)));

        Claims claims = validator.validate(token);

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.get(JwtClaimNames.CORRELATION_ID, String.class)).isEqualTo("corr-abc-123");
        assertThat(claims.get(JwtClaimNames.ROLES, String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        JwtValidator validator = newValidator();
        String token = signToken(
                "a-completely-different-secret-key-that-is-also-long-enough",
                "user-42",
                "corr-abc-123",
                Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)));

        assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtValidator validator = newValidator();
        String token = signToken(SECRET, "user-42", "corr-abc-123", Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));

        assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(JwtException.class);
    }
}
