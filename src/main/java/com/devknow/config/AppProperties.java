package com.devknow.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "devknow")
public record AppProperties(Jwt jwt, Storage storage, Cors cors, Admin admin,
                            Ingestion ingestion, Rag rag, Ai ai) {

    public record Jwt(String secret, Duration expiration) {}
    public record Storage(String dir) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Admin(String email, String password, String fullName) {}
    public record Ingestion(DataSize maxFileSize, int chunkSize, int chunkOverlap, int embeddingBatchSize) {}
    public record Rag(int topK, double minScore, int maxContextChars, int historyMessages) {}
    public record Ai(String provider, String baseUrl, String apiKey, String embeddingModel, String chatModel,
                     int embeddingDimension, Duration timeout, int maxRetries, double temperature) {}
}
