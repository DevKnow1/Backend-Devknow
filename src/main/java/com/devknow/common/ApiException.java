package com.devknow.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Long conversationId;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Long conversationId) {
        super(message);
        this.status = status;
        this.conversationId = conversationId;
    }

    public HttpStatus getStatus() { return status; }
    public Long getConversationId() { return conversationId; }
}
