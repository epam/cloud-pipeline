package com.epam.pipeline.cmd;

public class PipelineCLIException extends RuntimeException {

    public PipelineCLIException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
