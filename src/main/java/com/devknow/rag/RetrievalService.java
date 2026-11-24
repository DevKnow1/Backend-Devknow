package com.devknow.rag;

import com.devknow.ai.EmbeddingClient;
import com.devknow.config.AppProperties;
import java.util.List;
import org.springframework.stereotype.Service;

/** RF-14, RF-15, RF-17: embedding de la consulta + top-k + umbral de relevancia configurable. */
@Service
public class RetrievalService {
    private final EmbeddingClient embeddings;
    private final VectorChunkStore store;
    private final AppProperties props;

    public RetrievalService(EmbeddingClient embeddings, VectorChunkStore store, AppProperties props) {
        this.embeddings = embeddings;
        this.store = store;
        this.props = props;
    }

    /** Devuelve solo fragmentos con score >= umbral, ordenados por relevancia. Lista vacía = evidencia insuficiente. */
    public List<RetrievedChunk> retrieve(String query) {
        float[] vector = embeddings.embedOne(query);
        return store.search(vector, props.rag().topK()).stream()
            .filter(c -> c.score() >= props.rag().minScore())
            .toList();
    }
}
