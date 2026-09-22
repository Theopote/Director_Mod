package com.director.actor.model.bbmodel;

import java.io.IOException;

/** Raised when a Blockbench .bbmodel file cannot be imported. */
public final class BbModelImportException extends IOException {
    public BbModelImportException(String message) {
        super(message);
    }

    public BbModelImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
