package com.epam.pipeline.exception;

public class PipelineResponseApiException extends PipelineResponseException {

    public PipelineResponseApiException(final String message) {
        super(message);
    }

    public PipelineResponseApiException(final Throwable cause) {
        super(cause);
    }

    public PipelineResponseApiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
