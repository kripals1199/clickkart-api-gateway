// src/main/java/com/clickkart/gateway/filter/JwtValidator.java
package com.clickkart.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

import com.clickkart.gateway.config.GatewaySecurityProperties;

/**
 * Validates HMAC-SHA256-signed JWTs minted by the Auth Service. The signing key is a
 * shared secret (JWT_SECRET) configured independently on both services - there is no
 * shared library, so both sides must be deployed with the same env var value.
 */
@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(GatewaySecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws JwtException if the token is malformed, expired, or has an invalid signature
     */
    public Claims validate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
