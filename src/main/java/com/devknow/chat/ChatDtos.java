package com.devknow.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(Long conversationId,
                              @NotBlank(message = "Escribe una pregunta") @Size(max = 2000, message = "La pregunta no puede superar los 2000 caracteres") String question) {}

    public record ChatResponse(Long conversationId, Long messageId, String answer,
                               boolean insufficientEvidence, List<SourceDto> sources) {}

    public record ConversationSummary(Long id, String title, Instant updatedAt) {}

    public record MessageDto(Long id, MessageRole role, String content, List<SourceDto> sources,
                             boolean insufficientEvidence, Instant createdAt) {}

    public record ConversationDetail(Long id, String title, List<MessageDto> messages) {}
}
