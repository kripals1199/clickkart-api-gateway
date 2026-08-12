// src/main/java/com/clickkart/gateway/config/GatewaySecurityProperties.java
package com.clickkart.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /** HMAC-SHA256 signing key shared with the Auth Service that mints these tokens. */
    private String jwtSecret;

    /** Ant-style path patterns reachable without a valid JWT (e.g. /api/v1/auth/login). */
    private List<String> publicPaths = new ArrayList<>();

    /**
     * Redis key prefix for revoked-jti entries - must match Auth Service's own
     * {@code auth.revocation-key-prefix} exactly, since Auth Service writes these keys on
     * logout and this service only reads them. No shared library, so this is a Java-level
     * default matching the value Auth Service's AuthProperties itself defaults to; both sides
     * are equally overridable via config if that ever needs to change.
     */
    private String revocationKeyPrefix = "revoked:jti:";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public String getRevocationKeyPrefix() {
        return revocationKeyPrefix;
    }

    public void setRevocationKeyPrefix(String revocationKeyPrefix) {
        this.revocationKeyPrefix = revocationKeyPrefix;
    }
}
