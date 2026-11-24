package com.devknow.document;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Page<Document> findByNameContainingIgnoreCase(String q, Pageable pageable);
    Page<Document> findByStatusAndNameContainingIgnoreCase(DocumentStatus status, String q, Pageable pageable);
    List<Document> findByStatusIn(Collection<DocumentStatus> statuses, Sort sort);
    List<Document> findByStatus(DocumentStatus status);
    long countByStatus(DocumentStatus status);
}
