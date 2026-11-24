package com.devknow.document;

import com.devknow.auth.AuthUser;
import com.devknow.common.ApiException;
import com.devknow.document.DocumentDtos.*;
import com.devknow.ingestion.IngestionJob;
import com.devknow.ingestion.IngestionService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Intencionalmente SIN @Transactional a nivel de clase: cada save() confirma antes de lanzar
 * el procesamiento asíncrono, para que la ingesta vea siempre el documento persistido.
 */
@Service
public class DocumentService {
    private final DocumentRepository repo;
    private final FileValidator validator;
    private final FileStorageService storage;
    private final IngestionService ingestion;

    public DocumentService(DocumentRepository repo, FileValidator validator, FileStorageService storage,
                           IngestionService ingestion) {
        this.repo = repo;
        this.validator = validator;
        this.storage = storage;
        this.ingestion = ingestion;
    }

    /** RF-06, RF-07, RF-12 */
    public List<UploadResult> upload(List<MultipartFile> files, AuthUser admin) {
        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile f : files) {
            String filename = FileValidator.sanitizeFilename(f.getOriginalFilename());
            try {
                DocumentFormat fmt = validator.validate(f);
                Path path = storage.store(f, fmt);
                Document d = new Document();
                d.setName(filename);
                d.setOriginalFilename(filename);
                d.setFormat(fmt);
                d.setSizeBytes(f.getSize());
                d.setStatus(DocumentStatus.PENDING);
                d.setStoragePath(path.toString());
                d.setUploadedBy(admin.id());
                d = repo.save(d);
                ingestion.process(new IngestionJob(d.getId(), path, fmt, filename, null));
                results.add(new UploadResult(filename, true, null, DocumentResponse.from(d)));
            } catch (ApiException e) {
                results.add(new UploadResult(filename, false, e.getMessage(), null));
            }
        }
        return results;
    }

    /** RF-25 */
    public PageResponse<DocumentResponse> list(String q, DocumentStatus status, int page, int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String query = q == null ? "" : q.trim();
        var result = status == null
            ? repo.findByNameContainingIgnoreCase(query, p)
            : repo.findByStatusAndNameContainingIgnoreCase(status, query, p);
        return PageResponse.of(result, DocumentResponse::from);
    }

    /** Pantalla "Procesamiento" */
    public List<DocumentResponse> processing() {
        return repo.findByStatusIn(List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING), Sort.by(Sort.Direction.DESC, "updatedAt"))
            .stream().map(DocumentResponse::from).toList();
    }

    /** Tarjetas del panel de Documentos */
    public StatsResponse stats() {
        long pending = repo.countByStatus(DocumentStatus.PENDING);
        long processing = repo.countByStatus(DocumentStatus.PROCESSING);
        long available = repo.countByStatus(DocumentStatus.AVAILABLE);
        long error = repo.countByStatus(DocumentStatus.ERROR);
        return new StatsResponse(pending + processing + available + error, available, processing, pending, error);
    }

    /** RF-26 */
    public DocumentResponse get(Long id) {
        return DocumentResponse.from(find(id));
    }

    /** RF-27 */
    public DocumentResponse updateMetadata(Long id, UpdateDocumentRequest req) {
        Document d = find(id);
        d.setName(req.name().trim());
        d.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        return DocumentResponse.from(repo.save(d));
    }

    /** RF-28 / RF-29: el documento deja de estar disponible apenas inicia el reemplazo. */
    public DocumentResponse replace(Long id, MultipartFile file) {
        Document d = find(id);
        ensureNotBusy(d);
        DocumentFormat fmt = validator.validate(file);
        String filename = FileValidator.sanitizeFilename(file.getOriginalFilename());
        Path newPath = storage.store(file, fmt);
        Path oldPath = Path.of(d.getStoragePath());
        d.setStatus(DocumentStatus.PROCESSING);
        d.setErrorMessage(null);
        d = repo.save(d);
        ingestion.process(new IngestionJob(d.getId(), newPath, fmt, filename, oldPath));
        return DocumentResponse.from(d);
    }

    /** Reintento tras un error de procesamiento. */
    public DocumentResponse reprocess(Long id) {
        Document d = find(id);
        ensureNotBusy(d);
        d.setStatus(DocumentStatus.PROCESSING);
        d.setErrorMessage(null);
        d = repo.save(d);
        ingestion.process(new IngestionJob(d.getId(), Path.of(d.getStoragePath()), d.getFormat(), d.getOriginalFilename(), null));
        return DocumentResponse.from(d);
    }

    /** RF-30: los chunks/embeddings se eliminan por ON DELETE CASCADE. */
    public void delete(Long id) {
        Document d = find(id);
        ensureNotBusy(d);
        Path path = Path.of(d.getStoragePath());
        repo.delete(d);
        storage.delete(path);
    }

    public Document find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Documento no encontrado."));
    }

    private void ensureNotBusy(Document d) {
        if (d.getStatus() == DocumentStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "El documento se está procesando. Inténtalo cuando termine.");
        }
    }
}
