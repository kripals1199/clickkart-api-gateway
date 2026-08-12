// src/main/java/com/clickkart/gateway/dto/ApiResponse.java
package com.clickkart.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

/**
 * Standard REST API response envelope for uniform API design.
 * 
 * <p>Enforces consistent payload structure across all microservices:</p>
 * <ul>
 *   <li><b>Success (2xx):</b> Contains {@code data}, omits error fields.</li>
 *   <li><b>Client/Server Errors (4xx/5xx):</b> Contains {@code error} details, omits data.</li>
 *   <li>Null fields are stripped automatically via Jackson {@code NON_NULL}.</li>
 * </ul>
 *
 * @param <T> Payload type for successful responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"timestamp", "status", "success", "data", "error", "message", "path", "correlationId"})
public final class ApiResponse<T> {

    private final String timestamp;
    private final int status;
    private final boolean success;
    private final T data;
    private final Object error;
    private final String message;
    private final String path;
    private final String correlationId;

    private ApiResponse(int status, boolean success, T data, Object error, String message, String path, String correlationId) {
        this.timestamp = Instant.now().toString();
        this.status = status;
        this.success = success;
        this.data = data;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
    }

    // --- Success Factory Methods ---

    /**
     * Standard 200 OK success response.
     */
    public static <T> ApiResponse<T> success(T data, String path, String correlationId) {
        return new ApiResponse<>(200, true, data, null, null, path, correlationId);
    }

    /**
     * Custom HTTP Success response (e.g., 201 Created, 202 Accepted).
     */
    public static <T> ApiResponse<T> success(int status, T data, String path, String correlationId) {
        return new ApiResponse<>(status, true, data, null, null, path, correlationId);
    }

    // --- Error Factory Methods ---

    /**
     * Generic error response (4xx / 5xx).
     */
    public static <T> ApiResponse<T> error(int status, Object error, String message, String path, String correlationId) {
        return new ApiResponse<>(status, false, null, error, message, path, correlationId);
    }

    // --- Getters ---

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public Object getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}