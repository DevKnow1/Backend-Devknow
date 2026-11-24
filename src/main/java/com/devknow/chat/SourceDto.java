package com.devknow.chat;

public record SourceDto(int index, Long chunkId, Long documentId, String documentName,
                        Integer page, String section, String fragment, double score) {}
