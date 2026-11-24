package com.devknow.document;

import java.util.Locale;
import java.util.Optional;

public enum DocumentFormat {
    PDF("application/pdf"), MD("text/markdown"), TXT("text/plain");

    private final String mediaType;

    DocumentFormat(String mediaType) { this.mediaType = mediaType; }

    public String mediaType() { return mediaType; }

    public static Optional<DocumentFormat> fromFilename(String filename) {
        if (filename == null) return Optional.empty();
        String n = filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return Optional.of(PDF);
        if (n.endsWith(".md") || n.endsWith(".markdown")) return Optional.of(MD);
        if (n.endsWith(".txt")) return Optional.of(TXT);
        return Optional.empty();
    }
}
