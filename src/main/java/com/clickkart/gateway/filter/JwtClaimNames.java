// src/main/java/com/clickkart/gateway/filter/JwtClaimNames.java
package com.clickkart.gateway.filter;

/**
 * Custom (non-registered) claim keys read from every access token by
 * {@link JwtAuthenticationGlobalFilter}. No shared library exists between services (Rule 4),
 * so this is a deliberate local copy of the same literal values Auth Service's own
 * {@code com.clickkart.auth.jwt.JwtClaimNames} writes when it mints a token - if either
 * side's copy drifts, claim extraction silently breaks and needs a manual cross-check.
 *
 * <p>{@code ROLES} was previously {@code "roles"} here, which did not match Auth Service's
 * actual claim key ({@code "roleTypes"}) - confirmed via cross-check while building the
 * Notification/Audit Log services. That mismatch meant {@code claims.get(JwtClaimNames.ROLES,
 * String.class)} always returned {@code null}, so this filter forwarded an empty {@code
 * X-User-Roles} header to every downstream service on every request. Fixed to match the real
 * token content - Auth Service is the minting source of truth, so this copy must track it, not
 * the other way around.
 */
public final class JwtClaimNames {

    private JwtClaimNames() {}

    public static final String ROLES = "roleTypes";
    public static final String CORRELATION_ID = "correlationId";
}
