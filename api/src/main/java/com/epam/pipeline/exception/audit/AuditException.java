package com.epam.pipeline.exception.audit;

public class AuditException extends RuntimeException {

    public AuditException() {
        super();
    }

    public AuditException(final String message) {
        super(message);
    }

    public AuditException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AuditException(final Throwable cause) {
        super(cause);
    }

    protected AuditException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
