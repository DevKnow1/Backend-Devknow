package com.devknow.document;

import com.devknow.auth.AuthUser;
import com.devknow.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Abrir documento" desde las fuentes del chat: cualquier usuario autenticado, solo documentos disponibles. */
@RestController
@RequestMapping("/api/documents")
public class DocumentFileController {
    private final DocumentService service;

    public DocumentFileController(DocumentService service) { this.service = service; }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        Document d = service.find(id);
        if (!user.isAdmin() && d.getStatus() != DocumentStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Documento no encontrado.");
        }
        Path p = Path.of(d.getStoragePath());
        if (!Files.exists(p)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El archivo del documento no está disponible.");
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(d.getFormat().mediaType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(d.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
            .body(new FileSystemResource(p));
    }
}
