package com.devknow.document;

import com.devknow.common.ApiException;
import com.devknow.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Guarda los archivos con nombre aleatorio (nunca se usa el nombre enviado por el cliente como ruta). */
@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException { Files.createDirectories(root); }

    public Path store(MultipartFile file, DocumentFormat fmt) {
        Path target = root.resolve(UUID.randomUUID() + "." + fmt.name().toLowerCase(Locale.ROOT));
        try {
            file.transferTo(target);
            return target;
        } catch (IOException e) {
            log.error("No se pudo guardar el archivo", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo almacenar el archivo.");
        }
    }

    public void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo {}", path.getFileName());
        }
    }
}
