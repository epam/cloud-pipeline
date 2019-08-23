package com.epam.pipeline.tesadapter.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@RestControllerAdvice("com.epam.pipeline.tesadapter.controller")
public class TesExceptionHandler {

    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<String> handleUncaughtException(final Throwable exception, final WebRequest
            request) {
        log.error(exception.getMessage() + request.getDescription(true));
        return new ResponseEntity<>(exception.getMessage() + request.getDescription(true),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
