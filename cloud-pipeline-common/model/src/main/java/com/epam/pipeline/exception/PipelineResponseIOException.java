package com.epam.pipeline.exception;

public class PipelineResponseIOException extends PipelineResponseException {

    public PipelineResponseIOException(final String message) {
        super(message);
    }

    public PipelineResponseIOException(final Throwable cause) {
        super(cause);
    }

    public PipelineResponseIOException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
