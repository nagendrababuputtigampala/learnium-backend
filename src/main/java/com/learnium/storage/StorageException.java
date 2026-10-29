package com.learnium.storage;

/**
 * An object-storage operation failed. Messages carry the operation and the
 * storage key only — never file content — so they are safe to log.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
