package com.devknow.ingestion;

import com.devknow.document.DocumentFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** RF-08. PDF digitales con PDFBox (OCR fuera del MVP), Markdown por secciones y TXT con codificación controlada. */
@Component
public class TextExtractor {
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");

    public List<TextSegment> extract(Path file, DocumentFormat format) {
        try {
            return switch (format) {
                case PDF -> pdf(file);
                case MD -> markdown(readText(file));
                case TXT -> single(readText(file));
            };
        } catch (IOException e) {
            throw new DocumentProcessingException(
                "No se pudo leer el archivo. Verifica que no esté dañado ni protegido con contraseña.");
        }
    }

    private List<TextSegment> pdf(Path file) throws IOException {
        List<TextSegment> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = doc.getNumberOfPages();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text != null && !text.isBlank()) out.add(new TextSegment(text, p, null));
            }
        }
        if (out.isEmpty()) {
            throw new DocumentProcessingException(
                "El PDF no contiene texto extraíble (los PDF escaneados requieren OCR, no soportado en esta versión).");
        }
        return out;
    }

    private List<TextSegment> markdown(String text) {
        List<TextSegment> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String section = null;
        boolean inFence = false;
        for (String line : text.split("\\R", -1)) {
            if (line.trim().startsWith("```")) inFence = !inFence;
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                flush(out, buf, section);
                section = m.group(1).trim();
                if (section.length() > 250) section = section.substring(0, 250);
            }
            buf.append(line).append('\n');
        }
        flush(out, buf, section);
        if (out.isEmpty()) throw new DocumentProcessingException("El documento no contiene texto.");
        return out;
    }

    private void flush(List<TextSegment> out, StringBuilder buf, String section) {
        if (!buf.toString().isBlank()) out.add(new TextSegment(buf.toString(), null, section));
        buf.setLength(0);
    }

    private List<TextSegment> single(String text) {
        if (text.isBlank()) throw new DocumentProcessingException("El documento no contiene texto.");
        return List.of(new TextSegment(text, null, null));
    }

    private String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String s;
        try {
            s = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            s = new String(bytes, Charset.forName("windows-1252"));
        }
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
