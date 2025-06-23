package com.epam.pipeline.exception;

public class GCPAuthorizationException extends RuntimeException{
    public GCPAuthorizationException() {
        super("Authorization failed for GCP Artifact Registry notification");
    }

    public GCPAuthorizationException(String message) {
        super(String.format("Authorization failed for GCP Artifact Registry notification. %s", message));
    }
}
