package com.devknow.ingestion;

/** Error de procesamiento con mensaje seguro para mostrar al administrador (RF-13). */
public class DocumentProcessingException extends RuntimeException {
    public DocumentProcessingException(String message) { super(message); }
}
