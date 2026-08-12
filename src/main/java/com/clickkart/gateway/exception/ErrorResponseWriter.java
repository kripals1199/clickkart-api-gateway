// src/main/java/com/clickkart/gateway/exception/ErrorResponseWriter.java
package com.clickkart.gateway.exception;

import com.clickkart.gateway.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes every gateway-originated error (JWT rejection, rate limiting, no route found,
 * unexpected failures) as an ApiResponse in the ClickKart standard error shape (Rule 12).
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return writeToResponse(response, status, message, exchange.getRequest().getURI().getPath(), correlationId);
    }

    /**
     * Used by ErrorResponseDecoratingGlobalFilter for statuses set directly on the response
     * (e.g. RequestRateLimiter's 429) that never flow through an exception - the caller has
     * already set the status code on the given response.
     */
    public Mono<Void> writeToResponse(
            ServerHttpResponse response, HttpStatus status, String message, String path, String correlationId) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body =
                ApiResponse.error(status.value(), status.getReasonPhrase(), message, path, correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
