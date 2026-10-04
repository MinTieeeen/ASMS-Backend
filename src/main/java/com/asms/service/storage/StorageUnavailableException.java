package com.asms.service.storage;

import org.jspecify.annotations.Nullable;

/**
 * The object storage is not configured or did not answer. Answered as a 500 by the global handler: the user can only
 * try again later.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
