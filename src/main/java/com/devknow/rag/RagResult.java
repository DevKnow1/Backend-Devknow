package com.devknow.rag;

import com.devknow.chat.SourceDto;
import java.util.List;

public record RagResult(String answer, boolean insufficientEvidence, List<SourceDto> sources) {
    public static RagResult insufficient(String message) { return new RagResult(message, true, List.of()); }
}
