package com.devknow.ingestion;

import com.devknow.ai.EmbeddingClient;
import com.devknow.config.AppProperties;
import com.devknow.document.Document;
import com.devknow.document.DocumentRepository;
import com.devknow.document.DocumentStatus;
import com.devknow.document.FileStorageService;
import com.devknow.rag.VectorChunkStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Pipeline: extraer -> chunking -> embeddings -> persistir (en una sola transacción) -> AVAILABLE.
 * RF-29 / RNF-15: los chunks anteriores solo se reemplazan cuando los nuevos ya están listos, y mientras el documento no
 * esté AVAILABLE la recuperación lo ignora, de modo que nunca se mezclan versiones.
 */
@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documents;
    private final TextExtractor extractor;
    private final Chunker chunker;
    private final EmbeddingClient embeddings;
    private final VectorChunkStore chunkStore;
    private final FileStorageService storage;
    private final AppProperties props;

    public IngestionService(DocumentRepository documents, TextExtractor extractor, Chunker chunker,
                            EmbeddingClient embeddings, VectorChunkStore chunkStore, FileStorageService storage,
                            AppProperties props) {
        this.documents = documents;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.chunkStore = chunkStore;
        this.storage = storage;
        this.props = props;
    }

    @Async("ingestionExecutor")
    public void process(IngestionJob job) {
        Document doc = documents.findById(job.documentId()).orElse(null);
        if (doc == null) {
            storage.delete(job.file());
            return;
        }
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setErrorMessage(null);
        documents.save(doc);
        try {
            List<TextSegment> segments = extractor.extract(job.file(), job.format());
            List<TextChunk> chunks = chunker.chunk(segments);
            if (chunks.isEmpty()) throw new DocumentProcessingException("El documento no contiene texto procesable.");
            List<float[]> vectors = embedAll(chunks);
            chunkStore.replaceChunks(job.documentId(), chunks, vectors);

            Document done = documents.findById(job.documentId()).orElseThrow();
            boolean replacing = job.oldFile() != null && !job.oldFile().equals(job.file());
            done.setStoragePath(job.file().toString());
            done.setFormat(job.format());
            done.setOriginalFilename(job.originalFilename());
            done.setSizeBytes(sizeOf(job.file()));
            if (replacing) done.setVersion(done.getVersion() + 1);
            done.setStatus(DocumentStatus.AVAILABLE);
            done.setErrorMessage(null);
            documents.save(done);
            if (replacing) storage.delete(job.oldFile());
            log.info("Documento {} indexado: {} fragmentos", job.documentId(), chunks.size());
        } catch (DocumentProcessingException | com.devknow.ai.AiProviderException e) {
            fail(job, e.getMessage());
        } catch (Exception e) {
            log.error("Error procesando el documento {}", job.documentId(), e);
            fail(job, "Ocurrió un error interno al procesar el documento. Inténtalo nuevamente.");
        }
    }

    private List<float[]> embedAll(List<TextChunk> chunks) {
        int batch = Math.max(1, props.ingestion().embeddingBatchSize());
        List<float[]> all = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i += batch) {
            List<String> texts = chunks.subList(i, Math.min(i + batch, chunks.size()))
                .stream().map(TextChunk::content).toList();
            all.addAll(embeddings.embed(texts));
        }
        return all;
    }

    private void fail(IngestionJob job, String message) {
        boolean replacing = job.oldFile() != null && !job.oldFile().equals(job.file());
        String msg = replacing
            ? message + " Se conservó la versión anterior, pero el documento no estará disponible hasta reprocesarla o reemplazarla."
            : message;
        if (replacing) storage.delete(job.file());
        documents.findById(job.documentId()).ifPresent(d -> {
            d.setStatus(DocumentStatus.ERROR);
            d.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
            documents.save(d);
        });
    }

    private long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Si la app se reinició durante un procesamiento, el documento no queda "procesando" para siempre. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (DocumentStatus s : List.of(DocumentStatus.PROCESSING, DocumentStatus.PENDING)) {
            for (Document d : documents.findByStatus(s)) {
                d.setStatus(DocumentStatus.ERROR);
                d.setErrorMessage("El procesamiento se interrumpió. Usa «Reprocesar» para reintentarlo.");
                documents.save(d);
            }
        }
    }
}
