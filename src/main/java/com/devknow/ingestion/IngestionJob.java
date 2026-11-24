package com.devknow.ingestion;

import com.devknow.document.DocumentFormat;
import java.nio.file.Path;

/** oldFile != null indica un reemplazo: se elimina solo si el nuevo archivo se indexa correctamente. */
public record IngestionJob(Long documentId, Path file, DocumentFormat format, String originalFilename, Path oldFile) {}
