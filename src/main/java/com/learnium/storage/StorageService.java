package com.learnium.storage;

import java.util.UUID;

/**
 * Object storage for uploaded files (chapter PDFs). The rest of the code
 * talks only to this interface and to opaque keys: the local-disk
 * implementation serves development, the S3 implementation serves any
 * S3-compatible store (Cloudflare R2 in production) by configuration.
 */
public interface StorageService {

    /**
     * Stores {@code content} for one material and returns its storage key.
     * The key is built from the ids plus a sanitized form of the filename —
     * callers never supply path segments directly.
     */
    String store(UUID centreId, UUID materialId, String filename, byte[] content);

    /** Reads the object stored under {@code key}. */
    byte[] read(String key);

    /** Deletes the object stored under {@code key}; a missing key is a no-op. */
    void delete(String key);
}
