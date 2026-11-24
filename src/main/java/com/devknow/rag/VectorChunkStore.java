package com.devknow.rag;

import com.devknow.ingestion.TextChunk;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** RF-11 / RF-15: persistencia y búsqueda por similitud coseno con PostgreSQL + pgvector. */
@Repository
public class VectorChunkStore {
    private final JdbcTemplate jdbc;

    public VectorChunkStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Reemplaza todos los fragmentos del documento de forma atómica. */
    @Transactional
    public void replaceChunks(Long documentId, List<TextChunk> chunks, List<float[]> vectors) {
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        jdbc.batchUpdate(
            "INSERT INTO document_chunks (document_id, chunk_index, content, page_number, section, embedding) "
                + "VALUES (?, ?, ?, ?, ?, CAST(? AS vector))",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    TextChunk c = chunks.get(i);
                    ps.setLong(1, documentId);
                    ps.setInt(2, c.index());
                    ps.setString(3, c.content());
                    if (c.page() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, c.page());
                    if (c.section() == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, c.section());
                    ps.setString(6, toLiteral(vectors.get(i)));
                }

                @Override
                public int getBatchSize() { return chunks.size(); }
            });
    }

    /** Solo considera documentos AVAILABLE: evita fragmentos de documentos en reemplazo, con error o pendientes. */
    public List<RetrievedChunk> search(float[] query, int topK) {
        String lit = toLiteral(query);
        return jdbc.query(
            "SELECT c.id, c.document_id, d.name, c.chunk_index, c.content, c.page_number, c.section, "
                + "1 - (c.embedding <=> CAST(? AS vector)) AS score "
                + "FROM document_chunks c JOIN documents d ON d.id = c.document_id "
                + "WHERE d.status = 'AVAILABLE' "
                + "ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?",
            (rs, n) -> new RetrievedChunk(
                rs.getLong("id"), rs.getLong("document_id"), rs.getString("name"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getObject("page_number", Integer.class), rs.getString("section"),
                rs.getDouble("score")),
            lit, lit, topK);
    }

    static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.isFinite(v[i]) ? v[i] : 0f);
        }
        return sb.append(']').toString();
    }
}
