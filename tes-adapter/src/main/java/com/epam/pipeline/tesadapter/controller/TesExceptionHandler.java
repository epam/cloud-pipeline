package com.epam.pipeline.tesadapter.controller;

import com.epam.pipeline.tesadapter.common.MessageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@RestControllerAdvice()
public class TesExceptionHandler {

    @Autowired
    private MessageHelper messageHelper;

    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<String> handleUncaughtException(final Throwable exception, final WebRequest
            request) {
        log.error(messageHelper.getMessage("logger.error", request.getDescription(true)), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(exception.getMessage() + request.getDescription(true));

    }
}
