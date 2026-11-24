package com.devknow;

import static org.junit.jupiter.api.Assertions.*;

import com.devknow.ingestion.Chunker;
import com.devknow.ingestion.TextChunk;
import com.devknow.ingestion.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void splitsLongTextKeepingPageAndOverlap() {
        Chunker chunker = new Chunker(200, 40);
        String text = "palabra ".repeat(200);
        List<TextChunk> chunks = chunker.chunk(List.of(new TextSegment(text, 4, "2.1 Fases")));

        assertTrue(chunks.size() > 1);
        for (TextChunk c : chunks) {
            assertTrue(c.content().length() <= 200);
            assertEquals(4, c.page());
            assertEquals("2.1 Fases", c.section());
        }
        for (int i = 0; i < chunks.size(); i++) assertEquals(i, chunks.get(i).index());
    }

    @Test
    void shortTextProducesSingleChunk() {
        List<TextChunk> chunks = new Chunker(1000, 100).chunk(List.of(new TextSegment("Hola mundo", null, null)));
        assertEquals(1, chunks.size());
        assertEquals("Hola mundo", chunks.get(0).content());
        assertNull(chunks.get(0).page());
    }

    @Test
    void blankTextProducesNoChunks() {
        assertTrue(new Chunker(1000, 100).chunk(List.of(new TextSegment("   \n  ", 1, null))).isEmpty());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new Chunker(50, 10));
        assertThrows(IllegalArgumentException.class, () -> new Chunker(200, 150));
    }
}
