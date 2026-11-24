package com.devknow.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String message,
                       Map<String, String> fieldErrors, Long conversationId) {

    public static ApiError of(HttpStatus s, String message) {
        return new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), message, null, null);
    }

    public static ApiError of(HttpStatus s, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), message, fieldErrors, null);
    }
}
