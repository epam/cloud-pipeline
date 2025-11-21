package com.epam.pipeline.elasticsearch;

public class ElasticsearchException extends RuntimeException {

    public ElasticsearchException(final String message, final Throwable exception) {
        super(message, exception);
    }

    public ElasticsearchException(final String message) {
        super(message);
    }
}
