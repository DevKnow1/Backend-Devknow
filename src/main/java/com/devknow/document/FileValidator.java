package com.devknow.document;

import com.devknow.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/** RF-07 / RNF-06: valida extensión, tamaño, archivo vacío y firma real del contenido. */
public class FileValidator {
    private final long maxBytes;

    public FileValidator(long maxBytes) { this.maxBytes = maxBytes; }

    public DocumentFormat validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw bad("El archivo está vacío.");
        }
        DocumentFormat fmt = DocumentFormat.fromFilename(file.getOriginalFilename())
            .orElseThrow(() -> bad("Formato no admitido. Solo se aceptan archivos PDF, Markdown (.md) y texto plano (.txt)."));
        if (file.getSize() > maxBytes) {
            throw bad("El archivo supera el tamaño máximo de " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8192);
        } catch (IOException e) {
            throw bad("No se pudo leer el archivo.");
        }
        if (fmt == DocumentFormat.PDF) {
            if (head.length < 5 || !new String(head, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
                throw bad("El contenido del archivo no corresponde a un PDF válido.");
            }
        } else {
            for (byte b : head) {
                if (b == 0) throw bad("El archivo contiene datos binarios y no es texto válido.");
            }
        }
        return fmt;
    }

    public static String sanitizeFilename(String original) {
        String n = original == null ? "documento" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "documento";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static ApiException bad(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
}
