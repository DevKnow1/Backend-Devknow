package com.devknow;

import static org.junit.jupiter.api.Assertions.*;

import com.devknow.common.ApiException;
import com.devknow.document.DocumentFormat;
import com.devknow.document.FileValidator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileValidatorTest {
    private final FileValidator validator = new FileValidator(1024);

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    @Test
    void acceptsValidFormats() {
        assertEquals(DocumentFormat.PDF, validator.validate(file("a.pdf", "%PDF-1.7 ...".getBytes(StandardCharsets.ISO_8859_1))));
        assertEquals(DocumentFormat.MD, validator.validate(file("a.md", "# Titulo".getBytes(StandardCharsets.UTF_8))));
        assertEquals(DocumentFormat.TXT, validator.validate(file("a.TXT", "texto".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsEmptyUnsupportedOversizedAndSpoofedFiles() {
        assertThrows(ApiException.class, () -> validator.validate(file("a.pdf", new byte[0])));
        assertThrows(ApiException.class, () -> validator.validate(file("a.exe", "x".getBytes())));
        assertThrows(ApiException.class, () -> validator.validate(file("a.txt", new byte[2048])));
        assertThrows(ApiException.class, () -> validator.validate(file("falso.pdf", "no soy un pdf".getBytes())));
        assertThrows(ApiException.class, () -> validator.validate(file("bin.txt", new byte[]{65, 0, 66})));
    }

    @Test
    void sanitizesFilenames() {
        assertEquals("manual.pdf", FileValidator.sanitizeFilename("../../etc/manual.pdf"));
        assertEquals("manual.pdf", FileValidator.sanitizeFilename("C:\\docs\\manual.pdf"));
    }
}
