package com.devknow.rag;

public record RetrievedChunk(Long chunkId, Long documentId, String documentName, int chunkIndex,
                             String content, Integer page, String section, double score) {}
