package com.learnium.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-disk {@link StorageService} for development. Objects live under
 * {@code <localDir>/<centreId>/<materialId>/<sanitized filename>}. Keys are
 * relative paths built only from ids and a sanitized filename, and every
 * read/delete re-validates that the resolved path stays inside the root —
 * path traversal is impossible even with a hand-crafted key.
 */
@Service
@ConditionalOnProperty(name = "learnium.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path rootDir;

    public LocalStorageService(StorageProperties properties) {
        this.rootDir = Path.of(properties.getLocalDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID centreId, UUID materialId, String filename, byte[] content) {
        String key = centreId + "/" + materialId + "/" + sanitize(filename);
        Path target = resolveInsideRoot(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Failed to store object " + key, e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        Path target = resolveInsideRoot(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new StorageException("Failed to read object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolveInsideRoot(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Failed to delete object " + key, e);
        }
    }

    /** Keeps only a safe final path segment: letters, digits, dot, dash, underscore. */
    static String sanitize(String filename) {
        String base = filename == null ? "" : filename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")
                ? "file"
                : cleaned;
    }

    private Path resolveInsideRoot(String key) {
        Path resolved = rootDir.resolve(key).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new StorageException("Storage key escapes the storage root: " + key, null);
        }
        return resolved;
    }
}
