package com.devknow.ingestion;

import java.util.ArrayList;
import java.util.List;

/** RF-09: segmentación configurable (tamaño y solapamiento en caracteres) que conserva página/sección. */
public class Chunker {
    private final int size;
    private final int overlap;

    public Chunker(int size, int overlap) {
        if (size < 100) throw new IllegalArgumentException("chunk-size debe ser >= 100");
        if (overlap < 0 || overlap >= size / 2) throw new IllegalArgumentException("chunk-overlap debe estar entre 0 y chunk-size/2");
        this.size = size;
        this.overlap = overlap;
    }

    public List<TextChunk> chunk(List<TextSegment> segments) {
        List<TextChunk> out = new ArrayList<>();
        int idx = 0;
        for (TextSegment seg : segments) {
            String text = seg.text().replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            int len = text.length();
            int start = 0;
            while (start < len) {
                int end = Math.min(start + size, len);
                if (end < len) {
                    int cut = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf(' ', end));
                    if (cut > start + size / 2) end = cut;
                }
                String piece = text.substring(start, end).trim();
                if (!piece.isEmpty()) out.add(new TextChunk(idx++, piece, seg.page(), seg.section()));
                if (end >= len) break;
                int next = end - overlap;
                start = next > start ? next : end;
            }
        }
        return out;
    }
}
