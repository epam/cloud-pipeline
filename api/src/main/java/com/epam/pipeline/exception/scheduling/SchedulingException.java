package com.epam.pipeline.exception.scheduling;

public class SchedulingException extends RuntimeException {

    public SchedulingException(final String message) {
        super(message);
    }

    public SchedulingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
