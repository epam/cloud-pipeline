package com.epam.pipeline.exception.cluster;


public class NodeNotFoundException extends RuntimeException {

    public NodeNotFoundException(final String message) {
        super(message);
    }
}
