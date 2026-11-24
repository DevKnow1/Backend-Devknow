package com.devknow.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class DocumentDtos {
    private DocumentDtos() {}

    public record DocumentResponse(Long id, String name, String originalFilename, DocumentFormat format,
                                   DocumentStatus status, String errorMessage, String description,
                                   long sizeBytes, int version, Instant createdAt, Instant updatedAt) {
        public static DocumentResponse from(Document d) {
            return new DocumentResponse(d.getId(), d.getName(), d.getOriginalFilename(), d.getFormat(), d.getStatus(),
                d.getErrorMessage(), d.getDescription(), d.getSizeBytes(), d.getVersion(), d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record UpdateDocumentRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 200, message = "Máximo 200 caracteres") String name,
        @Size(max = 500, message = "Máximo 500 caracteres") String description) {}

    public record UploadResult(String filename, boolean accepted, String error, DocumentResponse document) {}

    public record StatsResponse(long total, long available, long processing, long pending, long error) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> p, java.util.function.Function<E, T> mapper) {
            return new PageResponse<>(p.getContent().stream().map(mapper).toList(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
        }
    }
}
