// src/main/java/com/clickkart/gateway/filter/JwtAuthenticationGlobalFilter.java
package com.clickkart.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.clickkart.gateway.config.GatewaySecurityProperties;
import com.clickkart.gateway.exception.ErrorResponseWriter;

/**
 * Edge authentication for every route. Public paths (Auth Service login/register/refresh,
 * health) pass through untouched - they legitimately have no correlation ID yet, since the
 * Auth Service is the one that mints it at login (Rule 13). Every other request must present
 * a valid, non-revoked JWT; on success, the user id, roles, and the correlationId JWT claim
 * are extracted and forwarded downstream as X-User-Id, X-User-Roles, and X-Correlation-Id
 * headers, so downstream services never need to re-validate the token themselves.
 *
 * <p>The revocation check (revoked:jti:&lt;jti&gt; in the same Redis instance used for rate
 * limiting) is what makes logout actually invalidate an access token immediately, instead of
 * leaving it valid until its natural expiry - local JWT signature validation alone can't do
 * that, since the token itself has no way to know it was logged out after being issued.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    /**
     * Exchange attribute mirror of the correlation ID, since exchange.mutate().request(...)
     * downstream (e.g. inside this same filter) creates a new exchange whose headers a
     * filter positioned OUTSIDE this one (by reference captured before mutation) can no
     * longer see - but the attributes map is shared across all mutate()-derived exchanges of
     * the same request, so ErrorResponseDecoratingGlobalFilter can still read it later.
     */
    public static final String CORRELATION_ID_ATTRIBUTE = "clickkart.correlationId";
    /** Also read by {@link com.clickkart.gateway.config.GatewayConfig}'s per-user rate-limit key resolver. */
    public static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final GatewaySecurityProperties securityProperties;
    private final ErrorResponseWriter errorResponseWriter;
    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return errorResponseWriter.write(
                    exchange, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header", null);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims;
        try {
            claims = jwtValidator.validate(token);
        } catch (JwtException | IllegalArgumentException e) {
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token", null);
        }

        String correlationId = claims.get(JwtClaimNames.CORRELATION_ID, String.class);
        if (correlationId == null || correlationId.isBlank()) {
            return errorResponseWriter.write(
                    exchange, HttpStatus.UNAUTHORIZED, "Token is missing required correlationId claim", null);
        }

        String jti = claims.getId();
        return isRevoked(jti)
                .flatMap(revoked -> {
                    if (revoked) {
                        return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Token has been revoked", null);
                    }

                    exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

                    String roles = claims.get(JwtClaimNames.ROLES, String.class);
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(USER_ID_HEADER, claims.getSubject())
                            .header(USER_ROLES_HEADER, roles == null ? "" : roles)
                            .header(CORRELATION_ID_HEADER, correlationId)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                // Redis is required here, not best-effort: without it there is no way to honor a
                // prior logout/revocation, so every access token would effectively become
                // un-revocable until it naturally expires. Fail the request with a clear 503
                // rather than let a raw DataAccessException fall through to
                // GlobalErrorWebExceptionHandler's generic "Unexpected gateway error" 500.
                .onErrorResume(DataAccessException.class, e -> errorResponseWriter.write(
                        exchange, HttpStatus.SERVICE_UNAVAILABLE, "Token revocation check unavailable", correlationId));
    }

    private Mono<Boolean> isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            // tokens without a jti can't be individually revoked - treat as not revoked
            // rather than failing every request that predates jti support
            return Mono.just(false);
        }
        return reactiveStringRedisTemplate.hasKey(securityProperties.getRevocationKeyPrefix() + jti);
    }

    private boolean isPublicPath(String path) {
        List<String> publicPaths = securityProperties.getPublicPaths();
        for (String pattern : publicPaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // Run early in the "pre" phase, well before routing/load-balancing/rate-limit filters.
        return -100;
    }
}
