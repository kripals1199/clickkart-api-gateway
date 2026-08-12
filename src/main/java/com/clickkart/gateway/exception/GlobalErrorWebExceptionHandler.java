// src/main/java/com/clickkart/gateway/exception/GlobalErrorWebExceptionHandler.java
package com.clickkart.gateway.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.clickkart.gateway.filter.JwtAuthenticationGlobalFilter;

/**
 * Catches everything that escapes route handling - no matching route, no healthy downstream
 * instance, rate limiting, or an unexpected failure - and renders it in the same standard
 * error shape (Rule 12) that JwtAuthenticationGlobalFilter uses for auth failures. Ordered
 * ahead of Boot's DefaultErrorWebExceptionHandler so it always wins.
 */
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (ex instanceof NotFoundException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "No available instance for the requested service";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Unexpected gateway error";
        }

        String correlationId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationGlobalFilter.CORRELATION_ID_HEADER);
        return errorResponseWriter.write(exchange, status, message, correlationId);
    }
}
