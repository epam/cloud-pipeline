package com.epam.pipeline.billingreportagent.exception;

public class BillingException extends RuntimeException {

    public BillingException(final String message) {
        super(message);
    }

    public BillingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
