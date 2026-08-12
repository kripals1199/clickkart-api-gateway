// src/main/java/com/clickkart/gateway/filter/ErrorResponseDecoratingGlobalFilter.java
package com.clickkart.gateway.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.clickkart.gateway.exception.ErrorResponseWriter;

/**
 * Some Gateway filters (RequestRateLimiter's 429 in particular) short-circuit by setting a
 * status code and calling response.setComplete() directly, without throwing - so they never
 * reach GlobalErrorWebExceptionHandler and the client gets an empty body, breaking the
 * standard error shape (Rule 12). This filter wraps the response so that any setComplete()
 * on an error status with nothing written yet gets our standard JSON body instead.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseDecoratingGlobalFilter implements GlobalFilter, Ordered {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                HttpStatusCode statusCode = getStatusCode();
                if (statusCode != null && statusCode.isError() && !isCommitted()) {
                    HttpStatus status = HttpStatus.resolve(statusCode.value());
                    if (status == null) {
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                    }
                    String correlationId = (String) exchange.getAttributes()
                            .get(JwtAuthenticationGlobalFilter.CORRELATION_ID_ATTRIBUTE);
                    return errorResponseWriter.writeToResponse(
                            this, status, status.getReasonPhrase(), exchange.getRequest().getURI().getPath(), correlationId);
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Outermost wrapper: must run before JwtAuthenticationGlobalFilter and every
        // route-level filter (including RequestRateLimiter) so it can decorate their response.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
