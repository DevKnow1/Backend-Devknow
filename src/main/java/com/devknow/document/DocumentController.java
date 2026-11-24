package com.devknow.document;

import com.devknow.auth.AuthUser;
import com.devknow.common.ApiException;
import com.devknow.document.DocumentDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Módulo de administración documental (RF-05, RF-25 a RF-30). Solo ADMIN. */
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasRole('ADMIN')")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadResult>> upload(@RequestParam("files") List<MultipartFile> files,
                                                     @AuthenticationPrincipal AuthUser admin) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecciona al menos un archivo.");
        }
        List<UploadResult> results = service.upload(files, admin);
        boolean anyAccepted = results.stream().anyMatch(UploadResult::accepted);
        return ResponseEntity.status(anyAccepted ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(results);
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) DocumentStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/stats")
    public StatsResponse stats() { return service.stats(); }

    @GetMapping("/processing")
    public List<DocumentResponse> processing() { return service.processing(); }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) { return service.get(id); }

    @PatchMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDocumentRequest req) {
        return service.updateMetadata(id, req);
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> replace(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(service.replace(id, file));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.reprocess(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
