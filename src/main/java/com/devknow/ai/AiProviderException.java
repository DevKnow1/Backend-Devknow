package com.devknow.ai;

public class AiProviderException extends RuntimeException {
    public enum Kind { QUOTA, UNAVAILABLE, INVALID_RESPONSE }

    private final Kind kind;

    public AiProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
